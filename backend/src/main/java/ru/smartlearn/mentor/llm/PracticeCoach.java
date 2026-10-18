package ru.smartlearn.mentor.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.smartlearn.mentor.config.GigaChatProperties;
import ru.smartlearn.mentor.dialog.Role;
import ru.smartlearn.mentor.score.CompetencyMap;
import ru.smartlearn.mentor.score.HeuristicAssessor;
import ru.smartlearn.mentor.score.PracticeGrade;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Даёт задание по слабому месту и разбирает попытку.
 * Тема закрывается только на максимальный балл 3.
 */
@Service
public class PracticeCoach {

	private static final Logger log = LoggerFactory.getLogger(PracticeCoach.class);

	private static final String SYSTEM_PROMPT = """
			Ты тренер по владению ИИ-инструментами. Оцени только ответ сотрудника и не дописывай за него решение.
			Смотри только пункты из блока «В ответе нужно». Других критериев нет.
			Пункт закрыт, если человек его назвал своими словами. Не требуй готовый текст user story, список критериев приёмки, акторов или пример формулировки.
			Не требуй названия инструментов и систем. Не требуй Confluence, Jira, Slack и любые другие продукты.
			Не требуй согласование, прозрачность для стейкхолдеров, фиксацию утверждений, галлюцинации и ответственность, если этого пункта нет в задании.
			Если каждый пункт назван, это 3.
			0 — нет сути или «не знаю». 1 — назван один пункт. 2 — названы почти все. 3 — названы все.
			Пример. Ответ «Роль аналитика, цель — user story с критериями приёмки, сверю текст с заметкой и уберу лишнее» на задание про промпт — это 3.
			Если балл меньше 3: whyNotMax — одно предложение, какой пункт не назван. techniques — одно предложение, как назвать именно его, без примера готового ответа и без слова «например». nextTask — новый короткий кейс с другими фактами и тем же блоком «В ответе нужно» дословно. Не пиши «дополните прошлый ответ» и не давай решение.
			Если балл 3, поля whyNotMax, techniques и nextTask оставь пустыми строками.
			Верни только JSON:
			{"score":0,"whyNotMax":"...","techniques":"...","nextTask":"..."}
			""";

	private static final String TECHNIQUES = "Закрой только недостающий пункт из блока «В ответе нужно». Названия систем и каналов не нужны.";

	private static final String PROMPT_CHECK = """
			В ответе нужно назвать три вещи. Сам текст истории писать не нужно:
			1. Роль модели и цель промпта.
			2. Что на выходе будет user story и критерии приёмки.
			3. Как сверишь результат с заметкой и уберёшь лишнее.""";

	private static final String VALIDATION_CHECK = """
			В ответе нужно:
			1. Как сравнишь текст модели с источником.
			2. Какую одну правку промпта добавишь, чтобы расхождение всплывало сразу.""";

	private static final String WORKFLOW_CHECK = """
			В ответе нужно:
			1. Какой текст подаёшь на вход.
			2. Что считаешь готовым выходом.
			3. Как поймёшь, что модель ничего не додумала.""";

	private static final String LIMITS_CHECK = """
			В ответе нужно:
			1. Какие данные в промпт не отправляешь.
			2. Какую часть задачи сделаешь сам, без модели.
			3. Чем заменишь реальные данные, если нужен учебный пример.""";

	private static final String ITERATE_CHECK = """
			В ответе нужно:
			1. Какой кусок контекста добавишь во второй запрос.
			2. Как сравнишь второй ответ с первым.""";

	private static final String DEV_PROMPT_CHECK = """
			В ответе нужно:
			1. Что напишешь про стек и ограничение.
			2. Какой формат результата попросишь.
			3. Что проверишь в ответе до коммита.""";

	private static final String VERIFY_CHECK = """
			В ответе нужно:
			1. Какой тест или проверку запустишь.
			2. Что смотришь в diff, прежде чем принять патч.""";

	private static final String DEV_FLOW_CHECK = """
			В ответе нужно:
			1. Что подаёшь модели на вход.
			2. Что проверяешь на выходе сам.""";

	private static final String DEV_LIMITS_CHECK = """
			В ответе нужно:
			1. Что нельзя класть в промпт.
			2. Как сделать обезличенный пример для вопроса модели.""";

	private static final String DEV_ITERATE_CHECK = """
			В ответе нужно:
			1. Какой контекст добавишь в следующий запрос.
			2. Какой проверкой поймёшь, что вторая версия лучше.""";

	private static final Map<String, String> TASKS = Map.ofEntries(
			Map.entry("a-prompts", "Кейс: заметка «Клиент сам смотрит статус заявки, операторы больше не ходят в три системы».\n" + PROMPT_CHECK),
			Map.entry("a-validation", "Кейс: в источнике срок «5 рабочих дней», модель написала «5 календарных».\n" + VALIDATION_CHECK),
			Map.entry("a-workflow", "Кейс: из протокола встречи нужен список открытых вопросов.\n" + WORKFLOW_CHECK),
			Map.entry("a-limits", "Кейс: в заметке есть ФИО клиента и номер паспорта, нужно описать процесс выплаты.\n" + LIMITS_CHECK),
			Map.entry("a-iterate", "Кейс: модель вернула user story без критериев приёмки.\n" + ITERATE_CHECK),
			Map.entry("d-prompts", "Кейс: нужна ручка POST /orders на Spring Boot 3.\n" + DEV_PROMPT_CHECK),
			Map.entry("d-verify", "Кейс: модель прислала патч, тест на пустой заказ падает.\n" + VERIFY_CHECK),
			Map.entry("d-workflow", "Кейс: каждый день разбираешь ревью своих мелких правок.\n" + DEV_FLOW_CHECK),
			Map.entry("d-limits", "Кейс: в продовом конфиге лежат пароль базы и боевой URL.\n" + DEV_LIMITS_CHECK),
			Map.entry("d-iterate", "Кейс: первый diff не компилируется.\n" + DEV_ITERATE_CHECK)
	);

	private static final Map<String, String> FOLLOWS = Map.ofEntries(
			Map.entry("a-prompts", "Кейс: заметка «Партнёры хотят ежедневную выгрузку отчёта, сейчас его собирают вручную».\n" + PROMPT_CHECK),
			Map.entry("a-validation", "Кейс: в источнике лимит «одна заявка на клиента», модель написала «без ограничений».\n" + VALIDATION_CHECK),
			Map.entry("a-workflow", "Кейс: из письма заказчика нужно выделить акторов и цели.\n" + WORKFLOW_CHECK),
			Map.entry("a-limits", "Кейс: в переписке есть телефон клиента и сумма выплаты, нужно описать статус заявки.\n" + LIMITS_CHECK),
			Map.entry("a-iterate", "Кейс: модель смешала две разные цели в одной user story.\n" + ITERATE_CHECK),
			Map.entry("d-prompts", "Кейс: нужна ручка GET /orders/{id} на Spring Boot 3, без лишних зависимостей.\n" + DEV_PROMPT_CHECK),
			Map.entry("d-verify", "Кейс: модель прислала патч, тест на повторный платёж стал красным.\n" + VERIFY_CHECK),
			Map.entry("d-workflow", "Кейс: перед коммитом просишь модель коротко объяснить свой diff.\n" + DEV_FLOW_CHECK),
			Map.entry("d-limits", "Кейс: в примере для модели оказались токен и внутренний адрес сервиса.\n" + DEV_LIMITS_CHECK),
			Map.entry("d-iterate", "Кейс: второй diff компилируется, но ломает старый тест.\n" + DEV_ITERATE_CHECK)
	);

	private final GigaChatProperties properties;
	private final HeuristicAssessor heuristicAssessor;
	private final LlmClient llmClient;
	private final CompetencyMap competencyMap;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	/**
	 * Собирает тренера: GigaChat, если креды заданы, иначе локальная рубрика.
	 *
	 * @param properties креды GigaChat
	 * @param heuristicAssessor запасная оценка текста
	 * @param llmClient клиент модели
	 * @param competencyMap критерии темы для промпта
	 */
	public PracticeCoach(
			GigaChatProperties properties,
			HeuristicAssessor heuristicAssessor,
			LlmClient llmClient,
			CompetencyMap competencyMap
	) {
		this.properties = properties;
		this.heuristicAssessor = heuristicAssessor;
		this.llmClient = llmClient;
		this.competencyMap = competencyMap;
	}

	/**
	 * Тренажёр для тестов без облака.
	 *
	 * @return тренер на локальной рубрике
	 */
	public static PracticeCoach heuristicOnly() {
		return new PracticeCoach(
				new GigaChatProperties("", "", "", "", "", "", 0, 200),
				new HeuristicAssessor(),
				null,
				new CompetencyMap()
		);
	}

	/**
	 * Возвращает стартовое задание темы.
	 *
	 * @param questionId код пробела
	 * @return формулировка или пустая строка, если темы нет
	 */
	public String taskFor(String questionId) {
		return TASKS.getOrDefault(questionId, "");
	}

	/**
	 * Оценивает попытку. Максимум — 3, ниже него возвращает разбор и похожее задание.
	 *
	 * @param role роль сотрудника
	 * @param questionId код компетенции
	 * @param title тема пробела
	 * @param task текст текущего задания
	 * @param answer ответ человека
	 * @return балл, разбор и следующее задание
	 */
	public PracticeGrade grade(Role role, String questionId, String title, String task, String answer) {
		if (covers(questionId, answer)) {
			return new PracticeGrade(3, true, "", "", "", "checklist");
		}
		if (properties.configured() && llmClient != null) {
			try {
				return fromModel(role, questionId, title, task, answer);
			} catch (RuntimeException exception) {
				log.warn("GigaChat не разобрал попытку тренажёра, считаю локально: {}", exception.getMessage());
			}
		}
		return local(questionId, task, answer);
	}

	/**
	 * Собирает разбор без модели, чтобы тренажёр не останавливался.
	 *
	 * @param questionId код компетенции
	 * @param task текущее задание
	 * @param answer ответ человека
	 * @return локальная оценка
	 */
	private PracticeGrade local(String questionId, String task, String answer) {
		int score = heuristicAssessor.scoreAnswer(answer);
		if (score >= 3) {
			return new PracticeGrade(3, true, "", "", "", "heuristic");
		}
		String why = "Не закрыт пункт из блока «В ответе нужно». " + heuristicAssessor.explain(score);
		return new PracticeGrade(score, false, why, TECHNIQUES, alternate(questionId, task), "heuristic");
	}

	/**
	 * Просит модель оценить попытку и дописать похожее задание, если балл ниже 3.
	 *
	 * @param role роль сотрудника
	 * @param questionId код компетенции
	 * @param title тема пробела
	 * @param task текст текущего задания
	 * @param answer ответ человека
	 * @return оценка модели
	 */
	private PracticeGrade fromModel(Role role, String questionId, String title, String task, String answer) {
		String focus = focusLine(role, questionId);
		String user = "Роль: " + role.title() + "\nТема: " + title + "\nСмотреть только это: " + focus
				+ "\nЗадание:\n" + task + "\nОтвет:\n" + answer;
		String raw = llmClient.complete(SYSTEM_PROMPT, user);
		int open = raw.indexOf('{');
		int close = raw.lastIndexOf('}');
		if (open < 0 || close <= open) {
			throw new IllegalStateException("В ответе модели нет JSON");
		}
		JsonNode root = jsonMapper.readTree(raw.substring(open, close + 1));
		int score = Math.max(0, Math.min(3, root.path("score").asInt(-1)));
		if (score < 0) {
			throw new IllegalStateException("В ответе модели нет балла");
		}
		if (score >= 3) {
			return new PracticeGrade(3, true, "", "", "", "gigachat");
		}
		String why = keep(text(root, "whyNotMax", "Не закрыт пункт из блока «В ответе нужно»."), task,
				"Не закрыт пункт из блока «В ответе нужно».");
		String techniques = keep(text(root, "techniques", TECHNIQUES), task, TECHNIQUES);
		String next = nextTask(questionId, task, root.path("nextTask").asString(""));
		return new PracticeGrade(score, false, why, techniques, next, "gigachat");
	}

	/**
	 * Берёт одну строку компетенции без общей шкалы диагностики.
	 * Общая шкала заставляет модель требовать темы, которых нет в задании.
	 *
	 * @param role роль сотрудника
	 * @param questionId код темы
	 * @return узкий критерий или пустая строка
	 */
	private String focusLine(Role role, String questionId) {
		try {
			String full = competencyMap.byId(role, questionId).criteria();
			int scale = full.indexOf("0 —");
			return scale > 0 ? full.substring(0, scale).trim() : full.trim();
		} catch (IllegalArgumentException exception) {
			return "";
		}
	}

	/**
	 * Оставляет следующий кейс модели, только если он не добавляет новых требований.
	 *
	 * @param questionId код темы
	 * @param current текущее задание
	 * @param modelText задание из ответа модели
	 * @return безопасный следующий кейс
	 */
	private String nextTask(String questionId, String current, String modelText) {
		String proposed = modelText == null ? "" : modelText.trim();
		if (proposed.isBlank() || drifts(proposed) || !proposed.contains("В ответе нужно")) {
			return alternate(questionId, current);
		}
		return proposed;
	}

	/**
	 * Возвращает запасной кейс той же темы с тем же списком пунктов.
	 *
	 * @param questionId код темы
	 * @param current текущее задание
	 * @return другой кейс или текущий, если запаса нет
	 */
	private String alternate(String questionId, String current) {
		String follow = FOLLOWS.getOrDefault(questionId, "");
		if (follow.isBlank()) {
			return current;
		}
		return follow.equals(current) ? TASKS.getOrDefault(questionId, follow) : follow;
	}

	/**
	 * Подменяет текст, если модель требует лишнее или говорит не про пункты задания.
	 *
	 * @param value текст модели
	 * @param task текущее задание со списком пунктов
	 * @param fallback безопасная замена
	 * @return исходный текст или замена
	 */
	private String keep(String value, String task, String fallback) {
		String lower = value.toLowerCase();
		if (drifts(value) || lower.contains("например") || lower.contains("должен ") || !onChecklist(value, task)) {
			return fallback;
		}
		return value;
	}

	/**
	 * Проверяет, что разбор ссылается на пункт из блока «В ответе нужно».
	 *
	 * @param value текст модели
	 * @param task текущее задание
	 * @return {@code true}, если текст про один из пунктов задания
	 */
	private boolean onChecklist(String value, String task) {
		int at = task.indexOf("В ответе нужно");
		String checklist = at >= 0 ? task.substring(at) : task;
		String lower = value.toLowerCase();
		String list = checklist.toLowerCase();
		String[] tokens = {"рол", "цел", "промпт", "выход", "user story", "критер", "свер", "замет", "лишн",
				"источник", "правк", "вход", "додум", "данн", "отправ", "обезлич", "контекст", "стек",
				"огранич", "формат", "провер", "коммит", "тест", "diff", "патч", "секрет", "парол", "токен"};
		for (String token : tokens) {
			if (list.contains(token) && lower.contains(token)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Считает тему закрытой, когда в ответе назван каждый пункт задания.
	 * Отрицание рядом со словом не считается.
	 *
	 * @param questionId код темы
	 * @param answer ответ человека
	 * @return {@code true}, если видимый список закрыт
	 */
	private boolean covers(String questionId, String answer) {
		String text = answer == null ? "" : answer.toLowerCase();
		List<List<String>> groups = checks(questionId);
		if (groups.isEmpty()) {
			return false;
		}
		for (List<String> group : groups) {
			boolean hit = false;
			for (String token : group) {
				if (mentioned(text, token)) {
					hit = true;
					break;
				}
			}
			if (!hit) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Ищет слово, перед которым нет отрицания.
	 *
	 * @param text ответ в нижнем регистре
	 * @param token фрагмент пункта
	 * @return {@code true}, если фрагмент назван, а не отвергнут
	 */
	private boolean mentioned(String text, String token) {
		int from = 0;
		while (from < text.length()) {
			int at = text.indexOf(token, from);
			if (at < 0) {
				return false;
			}
			String before = text.substring(Math.max(0, at - 14), at);
			if (!before.contains("не ")) {
				return true;
			}
			from = at + token.length();
		}
		return false;
	}

	/**
	 * Возвращает группы слов видимого списка для темы.
	 *
	 * @param questionId код темы
	 * @return группы, внутри группы достаточно одного слова
	 */
	private List<List<String>> checks(String questionId) {
		return switch (questionId) {
			case "a-prompts" -> List.of(
					List.of("рол", "аналитик"),
					List.of("user story"),
					List.of("критер"),
					List.of("свер", "замет"));
			case "a-validation" -> List.of(
					List.of("срав", "источник"),
					List.of("промпт", "правк", "запрос"));
			case "a-workflow" -> List.of(
					List.of("вход", "пода"),
					List.of("выход", "готов", "результат"),
					List.of("додум", "лишн", "свер"));
			case "a-limits" -> List.of(
					List.of("фио", "паспорт", "телефон", "нельзя", "не отправ"),
					List.of("сам", "без модели"),
					List.of("обезлич", "учебн", "пример", "замен"));
			case "a-iterate" -> List.of(
					List.of("контекст", "втор", "добав"),
					List.of("срав", "провер", "лучше"));
			case "d-prompts" -> List.of(
					List.of("стек", "spring", "верси", "огранич"),
					List.of("формат", "diff"),
					List.of("провер", "тест", "коммит"));
			case "d-verify" -> List.of(
					List.of("тест"),
					List.of("diff", "ревью", "патч"));
			case "d-workflow" -> List.of(
					List.of("вход", "пода"),
					List.of("провер", "выход"));
			case "d-limits" -> List.of(
					List.of("секрет", "парол", "токен", "нельзя"),
					List.of("обезлич", "пример"));
			case "d-iterate" -> List.of(
					List.of("контекст", "уточн", "добав"),
					List.of("тест", "провер"));
			default -> List.of();
		};
	}

	/**
	 * Ловит разбор, который уводит в продукты и процессы вне задания.
	 *
	 * @param value проверяемый текст
	 * @return {@code true}, если текст лучше заменить
	 */
	private boolean drifts(String value) {
		String lower = value.toLowerCase();
		if (lower.contains("confluence") || lower.contains("jira") || lower.contains("slack")
				|| lower.contains("trello") || lower.contains("notion") || lower.contains("дополн")
				|| lower.contains("стейкхолдер") || lower.contains("прозрач")
				|| lower.contains("галлюцин") || lower.contains("ответствен")
				|| lower.contains("согласован")) {
			return true;
		}
		return lower.contains("инструмент") && (lower.contains("соглас") || lower.contains("фиксац"));
	}

	/**
	 * Читает строку из JSON и подставляет запасной текст.
	 *
	 * @param root разобранный ответ модели
	 * @param field имя поля
	 * @param fallback текст, если поле пустое
	 * @return непустая строка
	 */
	private String text(JsonNode root, String field, String fallback) {
		String value = root.path(field).asString("").trim();
		return value.isBlank() ? fallback : value;
	}
}
