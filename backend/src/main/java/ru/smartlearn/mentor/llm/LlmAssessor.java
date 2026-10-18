package ru.smartlearn.mentor.llm;

import org.springframework.stereotype.Component;
import ru.smartlearn.mentor.dialog.MentorSession;
import ru.smartlearn.mentor.question.Question;
import ru.smartlearn.mentor.question.QuestionBank;
import ru.smartlearn.mentor.score.AnswerScore;
import ru.smartlearn.mentor.score.Assessment;
import ru.smartlearn.mentor.score.AssessmentComposer;
import ru.smartlearn.mentor.score.Assessor;
import ru.smartlearn.mentor.score.StoredAnswer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Просит модель оценить ответы и разобрать её JSON.
 * Уровень всё равно назначает {@link AssessmentComposer}, не модель.
 */
@Component
public class LlmAssessor implements Assessor {

	private static final String SYSTEM_PROMPT = """
			Ты оцениваешь диагностику владения ИИ-инструментами. Оценивай ответы сотрудника, не придумывай за него.
			Рубрика каждого ответа, строго:
			0 — нет сути, отписка, «не знаю», общие слова без действия
			1 — есть идея, но нет проверки качества или конкретики
			2 — конкретные действия и хотя бы один способ проверить результат
			3 — конкретный приём, проверка качества и осознанное ограничение (данные, галлюцинации, ответственность)
			Не завышай. Короткий слабый ответ не может получить 2 или 3.
			Верни только JSON без пояснений вокруг:
			{"scores":[{"id":"...","score":0,"reason":"..."}],"strengths":["..."],"gaps":["..."],"summary":"..."}
			В scores ровно по одному объекту на каждый вопрос, id копируй из задания.
			""";

	private final LlmClient client;
	private final QuestionBank questionBank;
	private final AssessmentComposer composer = new AssessmentComposer();
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	/**
	 * Собирает оценщика поверх любого клиента модели.
	 *
	 * @param client клиент GigaChat или тестовая заглушка
	 * @param questionBank банк заданий, чтобы напомнить фокус оценки
	 */
	public LlmAssessor(LlmClient client, QuestionBank questionBank) {
		this.client = client;
		this.questionBank = questionBank;
	}

	/**
	 * Отправляет ответы в модель и собирает отчёт из её JSON.
	 *
	 * @param session завершённая диагностика
	 * @return отчёт с пометкой {@code gigachat}
	 */
	@Override
	public Assessment assess(MentorSession session) {
		String raw = client.complete(SYSTEM_PROMPT, userPrompt(session));
		JsonNode root = jsonMapper.readTree(extractJson(raw));
		return composer.compose(
				session.role(),
				scores(session, root.path("scores")),
				strings(root.path("strengths")),
				strings(root.path("gaps")),
				root.path("summary").asString(""),
				"gigachat"
		);
	}

	/**
	 * Собирает текст со всеми ответами и фокусом каждого задания.
	 *
	 * @param session сессия с ролью, целью и ответами
	 * @return приглашение для модели
	 */
	private String userPrompt(MentorSession session) {
		StringBuilder prompt = new StringBuilder();
		prompt.append("Роль: ").append(session.role().title()).append('\n');
		prompt.append("Цель: ").append(session.goal()).append('\n');
		prompt.append("Текущий опыт: ").append(session.skills()).append('\n');
		for (StoredAnswer answer : session.answers()) {
			Question question = questionBank.forRole(session.role()).stream()
					.filter(item -> item.id().equals(answer.questionId()))
					.findFirst()
					.orElseThrow();
			prompt.append("\nЗадание id=").append(answer.questionId()).append('\n');
			prompt.append("Тема: ").append(answer.title()).append('\n');
			prompt.append("Фокус оценки: ").append(question.focus()).append('\n');
			prompt.append("Вопрос: ").append(answer.question()).append('\n');
			prompt.append("Ответ: ").append(answer.answer()).append('\n');
		}
		return prompt.toString();
	}

	/**
	 * Вырезает JSON, даже если модель обернула его в markdown.
	 *
	 * @param raw сырой ответ модели
	 * @return текст от первой фигурной скобки до последней
	 */
	private String extractJson(String raw) {
		String text = raw == null ? "" : raw.trim();
		int fence = text.indexOf("```");
		if (fence >= 0) {
			int start = text.indexOf('\n', fence);
			int end = text.indexOf("```", start + 1);
			if (start > 0 && end > start) {
				text = text.substring(start, end).trim();
			}
		}
		int open = text.indexOf('{');
		int close = text.lastIndexOf('}');
		if (open < 0 || close <= open) {
			throw new IllegalStateException("В ответе модели нет JSON");
		}
		return text.substring(open, close + 1);
	}

	/**
	 * Читает баллы в порядке заданий сессии и обрезает их до 0–3.
	 *
	 * @param session сессия с ожидаемыми id заданий
	 * @param scoresNode массив {@code scores}
	 * @return оценки по всем заданиям
	 */
	private List<AnswerScore> scores(MentorSession session, JsonNode scoresNode) {
		Map<String, JsonNode> byId = new HashMap<>();
		if (scoresNode.isArray()) {
			for (JsonNode node : scoresNode) {
				byId.put(node.path("id").asString(""), node);
			}
		}
		List<AnswerScore> scores = new ArrayList<>();
		for (StoredAnswer answer : session.answers()) {
			JsonNode node = byId.get(answer.questionId());
			if (node == null || node.isMissingNode()) {
				throw new IllegalStateException("Нет оценки для " + answer.questionId());
			}
			int score = Math.max(0, Math.min(3, node.path("score").asInt(0)));
			scores.add(new AnswerScore(answer.questionId(), answer.title(), score, node.path("reason").asString("").trim()));
		}
		return scores;
	}

	/**
	 * Читает массив строк из JSON.
	 *
	 * @param node узел массива
	 * @return строки без пустых значений
	 */
	private List<String> strings(JsonNode node) {
		List<String> values = new ArrayList<>();
		if (!node.isArray()) {
			return values;
		}
		for (JsonNode item : node) {
			String value = item.asString("").trim();
			if (!value.isEmpty()) {
				values.add(value);
			}
		}
		return values;
	}
}
