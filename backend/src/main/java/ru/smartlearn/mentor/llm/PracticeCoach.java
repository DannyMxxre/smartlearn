package ru.smartlearn.mentor.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.smartlearn.mentor.config.YandexProperties;
import ru.smartlearn.mentor.dialog.Role;
import ru.smartlearn.mentor.score.HeuristicAssessor;
import ru.smartlearn.mentor.score.PracticeGrade;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * Даёт одно практическое задание по пробелу и ставит зачёт.
 * Положительный зачёт — балл не ниже 2.
 */
@Service
public class PracticeCoach {

	private static final Logger log = LoggerFactory.getLogger(PracticeCoach.class);

	private static final String SYSTEM_PROMPT = """
			Ты принимаешь одну попытку тренажёра по владению ИИ-инструментами.
			Оцени только ответ сотрудника. Не дописывай за него решение.
			Рубрика:
			0 — нет сути или «не знаю»
			1 — идея без проверки результата
			2 — конкретные действия и способ проверить качество
			3 — приём, проверка и ограничение (данные, галлюцинации, ответственность)
			Верни только JSON: {"score":0,"feedback":"2-4 предложения по-русски"}
			""";

	private static final Map<String, String> TASKS = Map.of(
			"a-task", "Собери промпт для сырой расшифровки созвона: роль, цель, формат user story и критерии приёмки. Напиши, что проверишь сам перед передачей команде.",
			"a-check", "ИИ дописал требование, которое спорит с первым абзацем ТЗ. Опиши, как поймаешь противоречие, и какой проверкой заставишь модель показать его сразу.",
			"a-practice", "Заметка: «Клиент видит статус выплаты сам, оператор больше не копирует его из трёх систем, юристам нужно согласие, интеграция неясна». Выдели акторов, цели и открытые вопросы. Напиши, как проверишь, что ничего не додумано.",
			"a-limits", "Назови аналитическую задачу, где ИИ лучше не использовать, и какие персональные данные нельзя отправлять в промпт. Как отделишь учебный пример от реального требования?",
			"d-bug", "Тест падает в чужом сервисе. Опиши, как попросишь ИИ найти причину, и какой чеклист ревью не даст принять плохой diff. Упомяни тест и проверку результата.",
			"d-api", "Модель предложила метод Spring, которого нет в твоей версии. Как ограничишь промпт версией и исходниками, чтобы таких советов стало меньше, и как проверишь ответ?",
			"d-practice", "Напиши промпт на REST-ручку Spring Boot: валидация, понятные ошибки и тест. Перечисли, что проверишь в diff до коммита.",
			"d-secrets", "Что нельзя класть в промпт и в контекст агента? Покажи, как из прод-конфига сделать обезличенный пример без секретов."
	);

	private final YandexProperties properties;
	private final HeuristicAssessor heuristicAssessor;
	private final LlmClient llmClient;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	/**
	 * Собирает тренера: облако, если оно настроено, иначе локальная рубрика.
	 *
	 * @param properties ключ YandexGPT
	 * @param heuristicAssessor запасная оценка текста
	 * @param llmClient клиент модели
	 */
	public PracticeCoach(YandexProperties properties, HeuristicAssessor heuristicAssessor, LlmClient llmClient) {
		this.properties = properties;
		this.heuristicAssessor = heuristicAssessor;
		this.llmClient = llmClient;
	}

	/**
	 * Тренажёр для тестов без облака.
	 *
	 * @return тренер на локальной рубрике
	 */
	public static PracticeCoach heuristicOnly() {
		return new PracticeCoach(
				new YandexProperties("", "", "", "", "Api-Key", 0, 200),
				new HeuristicAssessor(),
				null
		);
	}

	/**
	 * Возвращает текст задания для темы.
	 *
	 * @param questionId код пробела
	 * @return формулировка или пустая строка, если темы нет
	 */
	public String taskFor(String questionId) {
		return TASKS.getOrDefault(questionId, "");
	}

	/**
	 * Ставит балл попытке. Зачёт начинается с 2.
	 *
	 * @param role роль сотрудника
	 * @param title тема пробела
	 * @param task текст задания
	 * @param answer ответ человека
	 * @return оценка и комментарий
	 */
	public PracticeGrade grade(Role role, String title, String task, String answer) {
		if (properties.configured() && llmClient != null) {
			try {
				return fromModel(role, title, task, answer);
			} catch (RuntimeException exception) {
				log.warn("YandexGPT не принял попытку тренажёра, считаю локально: {}", exception.getMessage());
			}
		}
		int score = heuristicAssessor.scoreAnswer(answer);
		String feedback = score >= 2
				? "В ответе есть конкретный приём и проверка результата."
				: "Пока не хватает конкретики: опиши действия и как проверишь качество.";
		return new PracticeGrade(score, score >= 2, feedback, "heuristic");
	}

	/**
	 * Просит модель поставить балл и обрезает его до 0–3.
	 *
	 * @param role роль сотрудника
	 * @param title тема пробела
	 * @param task текст задания
	 * @param answer ответ человека
	 * @return оценка модели
	 */
	private PracticeGrade fromModel(Role role, String title, String task, String answer) {
		String user = "Роль: " + role.title() + "\nТема: " + title + "\nЗадание: " + task + "\nОтвет: " + answer;
		String raw = llmClient.complete(SYSTEM_PROMPT, user);
		int open = raw.indexOf('{');
		int close = raw.lastIndexOf('}');
		if (open < 0 || close <= open) {
			throw new IllegalStateException("В ответе модели нет JSON");
		}
		JsonNode root = jsonMapper.readTree(raw.substring(open, close + 1));
		int score = Math.max(0, Math.min(3, root.path("score").asInt(0)));
		String feedback = root.path("feedback").asString("").trim();
		if (feedback.isBlank()) {
			feedback = score >= 2 ? "Зачёт." : "Пока рано закрывать тему.";
		}
		return new PracticeGrade(score, score >= 2, feedback, "yandex");
	}
}
