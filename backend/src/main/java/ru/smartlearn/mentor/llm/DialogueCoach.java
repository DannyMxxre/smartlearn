package ru.smartlearn.mentor.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.smartlearn.mentor.config.GigaChatProperties;
import ru.smartlearn.mentor.dialog.MentorSession;
import ru.smartlearn.mentor.dialog.Phase;
import ru.smartlearn.mentor.dialog.Role;
import ru.smartlearn.mentor.score.AnswerScore;
import ru.smartlearn.mentor.score.AssessmentComposer;
import ru.smartlearn.mentor.score.BonusTask;
import ru.smartlearn.mentor.score.Competency;
import ru.smartlearn.mentor.score.CompetencyMap;
import ru.smartlearn.mentor.score.CompetencyRollup;
import ru.smartlearn.mentor.score.HeuristicAssessor;
import ru.smartlearn.mentor.score.PlanTemplates;
import ru.smartlearn.mentor.score.StoredAnswer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Ведёт открытый диалог по карте компетенций и два бонусных кейса.
 * Границы 6–10 реплик и переход к практике решает код. Модель ставит баллы.
 */
@Service
public class DialogueCoach {

	private static final Logger log = LoggerFactory.getLogger(DialogueCoach.class);

	private static final int MIN_REPLIES = 6;
	private static final int MAX_REPLIES = 10;

	private static final String SYSTEM_PROMPT = """
			Ты оцениваешь один ответ сотрудника по карте компетенций владения ИИ-инструментами.
			Оценивай только то, что человек написал. Не дописывай за него решение и не назначай уровень.
			Балл строго 0–3 по критериям компетенции.
			Верни только JSON:
			{"scores":[{"id":"...","score":0,"reason":"коротко"}],"comment":"1-2 предложения","nextQuestion":"одно уточнение по текущей теме, если ответ слабый"}
			В scores включай только компетенции, которые ответ реально затронул. id копируй из карты.
			""";

	private final GigaChatProperties properties;
	private final HeuristicAssessor heuristicAssessor;
	private final LlmClient llmClient;
	private final CompetencyMap competencyMap;
	private final AssessmentComposer composer = new AssessmentComposer();
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	/**
	 * Собирает наставника диалога.
	 *
	 * @param properties креды GigaChat
	 * @param heuristicAssessor запасная оценка текста
	 * @param llmClient клиент модели, может быть заглушкой
	 * @param competencyMap карта компетенций и бонусные кейсы
	 */
	public DialogueCoach(
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
	 * Наставник для тестов и прогона без ключа.
	 *
	 * @return диалог на локальной рубрике
	 */
	public static DialogueCoach heuristicOnly() {
		return new DialogueCoach(
				new GigaChatProperties("", "", "", "", "", "", 0, 200),
				new HeuristicAssessor(),
				(system, user) -> "{}",
				new CompetencyMap()
		);
	}

	/**
	 * Открывает первую тему выбранной роли.
	 *
	 * @param session сессия после знакомства
	 * @return первый вопрос
	 */
	public String open(MentorSession session) {
		Competency first = competencyMap.forRole(session.role()).getFirst();
		session.setPhase(Phase.DIALOGUE);
		session.setCompetencyIndex(0);
		session.setFocusId(first.id());
		session.setFollowUp(false);
		session.setBonusIndex(-1);
		return "Дальше поговорим по делу, без анкеты из четырёх пунктов. Буду спрашивать про разные стороны работы с ИИ и иногда уточнять.\n\n" + first.prompt();
	}

	/**
	 * Принимает ответ в диалоге или на бонусном кейсе.
	 *
	 * @param session текущая сессия
	 * @param answer текст человека
	 * @return следующий вопрос или готовый отчёт
	 */
	public String answer(MentorSession session, String answer) {
		if (session.phase() == Phase.BONUS) {
			return onBonus(session, answer);
		}
		return onDialogue(session, answer);
	}

	/**
	 * Оценивает реплику и либо уточняет тему, либо переходит дальше.
	 *
	 * @param session сессия в открытом диалоге
	 * @param answer ответ человека
	 * @return следующая реплика наставника
	 */
	private String onDialogue(MentorSession session, String answer) {
		Competency focus = competencyMap.byId(session.role(), session.focusId());
		TurnGrade grade = grade(session, focus, answer);
		session.addObservation(new AnswerScore(focus.id(), focus.title(), grade.score(), grade.reason()));
		session.addAnswer(new StoredAnswer(focus.id(), focus.title(), focus.prompt(), answer));
		session.incrementReplies();
		rememberComment(session, grade.comment());

		if (session.followUp()) {
			session.setFollowUp(false);
			session.setCompetencyIndex(session.competencyIndex() + 1);
		} else if (grade.score() <= 1 && session.substantiveReplies() < MAX_REPLIES) {
			session.setFollowUp(true);
			String followUp = grade.followUpQuestion();
			if (followUp == null || followUp.isBlank()) {
				followUp = "Пока не хватает проверки результата. " + focus.prompt();
			}
			return followUp;
		} else {
			session.setCompetencyIndex(session.competencyIndex() + 1);
		}

		if (readyForBonus(session)) {
			return startBonus(session);
		}
		List<Competency> competencies = competencyMap.forRole(session.role());
		if (session.competencyIndex() >= competencies.size()) {
			Competency weakest = weakest(session, competencies);
			session.setFocusId(weakest.id());
			return "Вернёмся к теме «" + weakest.title() + "». " + weakest.prompt();
		}
		Competency next = competencies.get(session.competencyIndex());
		session.setFocusId(next.id());
		return "Дальше другая сторона работы.\n\n" + next.prompt();
	}

	/**
	 * Оценивает бонусный кейс и либо задаёт следующий, либо собирает отчёт.
	 *
	 * @param session сессия на практике
	 * @param answer ответ человека
	 * @return следующий кейс или отчёт
	 */
	private String onBonus(MentorSession session, String answer) {
		BonusTask task = competencyMap.bonuses(session.role()).get(session.bonusIndex());
		Competency focus = competencyMap.byId(session.role(), task.competencyId());
		TurnGrade grade = grade(session, focus, answer);
		session.addObservation(new AnswerScore(focus.id(), focus.title(), grade.score(), grade.reason()));
		session.addAnswer(new StoredAnswer(task.id(), task.title(), task.text(), answer));
		rememberComment(session, grade.comment());
		int next = session.bonusIndex() + 1;
		if (next < competencyMap.bonuses(session.role()).size()) {
			session.setBonusIndex(next);
			return bonusText(session, next);
		}
		return finish(session);
	}

	/**
	 * Проверяет, пора ли закрывать свободный диалог.
	 * Раньше шести реплик не заканчиваем, после десяти заканчиваем обязательно.
	 *
	 * @param session сессия диалога
	 * @return {@code true}, если можно переходить к практике
	 */
	private boolean readyForBonus(MentorSession session) {
		if (session.substantiveReplies() >= MAX_REPLIES) {
			return true;
		}
		return session.substantiveReplies() >= MIN_REPLIES
				&& session.competencyIndex() >= competencyMap.forRole(session.role()).size();
	}

	/**
	 * Открывает первый бонусный кейс.
	 *
	 * @param session сессия после основной диагностики
	 * @return текст задания
	 */
	private String startBonus(MentorSession session) {
		session.setPhase(Phase.BONUS);
		session.setFollowUp(false);
		session.setBonusIndex(0);
		return "Основной разговор собран. Дальше два дополнительных практических задания.\n\n" + bonusText(session, 0);
	}

	/**
	 * Формулирует бонусный кейс с номером.
	 *
	 * @param session сессия на практике
	 * @param index индекс кейса
	 * @return текст задания
	 */
	private String bonusText(MentorSession session, int index) {
		List<BonusTask> tasks = competencyMap.bonuses(session.role());
		BonusTask task = tasks.get(index);
		session.setFocusId(task.competencyId());
		return "Дополнительное практическое задание " + (index + 1) + " из " + tasks.size() + ".\n\n" + task.text();
	}

	/**
	 * Собирает отчёт по средним баллам компетенций.
	 *
	 * @param session завершённая диагностика
	 * @return текст отчёта для чата отдаёт вызывающий код через оценку сессии
	 */
	private String finish(MentorSession session) {
		List<AnswerScore> scores = CompetencyRollup.rollup(
				competencyMap.forRole(session.role()),
				session.observations()
		);
		session.complete(composer.compose(
				session.role(),
				scores,
				List.of(),
				List.of(),
				session.summaryComment(),
				PlanTemplates.forRole(session.role()),
				session.scoredBy()
		));
		return null;
	}

	/**
	 * Выбирает самую слабую уже затронутую компетенцию для дополнительного вопроса.
	 *
	 * @param session сессия с наблюдениями
	 * @param competencies карта роли
	 * @return компетенция с минимальным средним баллом
	 */
	private Competency weakest(MentorSession session, List<Competency> competencies) {
		List<AnswerScore> rolled = CompetencyRollup.rollup(competencies, session.observations());
		AnswerScore lowest = rolled.getFirst();
		for (AnswerScore score : rolled) {
			if (score.score() < lowest.score()) {
				lowest = score;
			}
		}
		return competencyMap.byId(session.role(), lowest.questionId());
	}

	/**
	 * Ставит балл текущей теме. При сбое модели остаётся локальная рубрика.
	 *
	 * @param session сессия
	 * @param focus тема вопроса
	 * @param answer ответ человека
	 * @return балл, причина и возможное уточнение
	 */
	private TurnGrade grade(MentorSession session, Competency focus, String answer) {
		if (properties.configured() && llmClient != null) {
			try {
				TurnGrade graded = fromModel(session, focus, answer);
				session.markScoredBy("gigachat");
				return graded;
			} catch (RuntimeException exception) {
				log.warn("GigaChat не оценил реплику, считаю локально: {}", exception.getMessage());
			}
		}
		session.markScoredBy("heuristic");
		int score = heuristicAssessor.scoreAnswer(answer);
		return new TurnGrade(score, heuristicAssessor.explain(score), "", "");
	}

	/**
	 * Просит модель оценить реплику и обрезает балл до 0–3.
	 *
	 * @param session сессия
	 * @param focus тема вопроса
	 * @param answer ответ человека
	 * @return оценка модели по фокусной компетенции
	 */
	private TurnGrade fromModel(MentorSession session, Competency focus, String answer) {
		String raw = llmClient.complete(SYSTEM_PROMPT, userPrompt(session, focus, answer));
		JsonNode root = jsonMapper.readTree(extractJson(raw));
		int score = -1;
		String reason = "";
		if (root.path("scores").isArray()) {
			for (JsonNode node : root.path("scores")) {
				String id = node.path("id").asString("");
				int value = Math.max(0, Math.min(3, node.path("score").asInt(0)));
				String nodeReason = node.path("reason").asString("").trim();
				if (!id.equals(focus.id()) && competencyExists(session.role(), id)) {
					Competency extra = competencyMap.byId(session.role(), id);
					session.addObservation(new AnswerScore(extra.id(), extra.title(), value, nodeReason));
				}
				if (id.equals(focus.id())) {
					score = value;
					reason = nodeReason;
				}
			}
		}
		if (score < 0) {
			int local = heuristicAssessor.scoreAnswer(answer);
			score = local;
			reason = "Модель не вернула балл этой темы. Локально: " + heuristicAssessor.explain(local);
		}
		if (reason.isBlank()) {
			reason = heuristicAssessor.explain(score);
		}
		return new TurnGrade(score, reason, root.path("comment").asString("").trim(), root.path("nextQuestion").asString("").trim());
	}

	/**
	 * Проверяет, что код компетенции есть в карте роли.
	 *
	 * @param role роль
	 * @param id код из JSON
	 * @return {@code true}, если компетенция известна
	 */
	private boolean competencyExists(Role role, String id) {
		for (Competency competency : competencyMap.forRole(role)) {
			if (competency.id().equals(id)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Собирает промпт с картой, фокусом и ответом.
	 *
	 * @param session сессия
	 * @param focus текущая компетенция
	 * @param answer ответ человека
	 * @return текст для модели
	 */
	private String userPrompt(MentorSession session, Competency focus, String answer) {
		StringBuilder prompt = new StringBuilder();
		prompt.append("Роль: ").append(session.role().title()).append('\n');
		prompt.append("Фокус: ").append(focus.id()).append('\n');
		prompt.append("Тема: ").append(focus.title()).append('\n');
		prompt.append("Критерии:\n").append(focus.criteria()).append('\n');
		prompt.append("Карта:\n");
		for (Competency competency : competencyMap.forRole(session.role())) {
			prompt.append("- ").append(competency.id()).append(": ").append(competency.title()).append('\n');
		}
		prompt.append("Ответ: ").append(answer);
		return prompt.toString();
	}

	/**
	 * Вырезает JSON из ответа модели.
	 *
	 * @param raw сырой текст
	 * @return объект от первой до последней фигурной скобки
	 */
	private String extractJson(String raw) {
		String text = raw == null ? "" : raw.trim();
		int open = text.indexOf('{');
		int close = text.lastIndexOf('}');
		if (open < 0 || close <= open) {
			throw new IllegalStateException("В ответе модели нет JSON");
		}
		return text.substring(open, close + 1);
	}

	/**
	 * Запоминает последний комментарий модели для итогового абзаца.
	 *
	 * @param session сессия
	 * @param comment комментарий или пустая строка
	 */
	private void rememberComment(MentorSession session, String comment) {
		if (comment != null && !comment.isBlank()) {
			session.setSummaryComment(comment);
		}
	}

	/**
	 * Балл одной реплики.
	 *
	 * @param score балл 0–3
	 * @param reason обоснование
	 * @param comment комментарий для итога
	 * @param followUpQuestion уточняющий вопрос, можно пустой
	 */
	private record TurnGrade(int score, String reason, String comment, String followUpQuestion) {
	}
}
