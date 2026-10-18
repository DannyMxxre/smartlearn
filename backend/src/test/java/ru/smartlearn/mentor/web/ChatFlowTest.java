package ru.smartlearn.mentor.web;

import org.junit.jupiter.api.Test;
import ru.smartlearn.mentor.config.GigaChatProperties;
import ru.smartlearn.mentor.dialog.SessionStore;
import ru.smartlearn.mentor.llm.DialogueCoach;
import ru.smartlearn.mentor.llm.PracticeCoach;
import ru.smartlearn.mentor.score.CompetencyMap;
import ru.smartlearn.mentor.score.HeuristicAssessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Прогоняет сценарий без облака.
 * Слабые и сильные ответы обязаны получить разные уровни.
 */
class ChatFlowTest {

	private static final String STRONG = """
			Сначала формулирую промпт: роль, цель, ограничение и формат ответа. \
			Прошу модель показать допущения. Результат проверяю по критериям приёмки и сверяю с исходным источником, \
			чтобы поймать галлюцинацию и противоречие. Секреты и персональные данные в промпт не отправляю, \
			беру обезличенный пример. Перед коммитом смотрю diff, гоняю тест и делаю ревью валидации.""";

	private final HeuristicAssessor heuristicAssessor = new HeuristicAssessor();

	/**
	 * Слабые отписки дают уровень «Новичок».
	 */
	@Test
	void weakAnswersStayNovice() {
		ChatResponse report = finish(new ChatService(new SessionStore()), "не знаю");
		assertEquals("Новичок", report.report().level());
		assertEquals(0, report.report().score());
		assertEquals("heuristic", report.report().scoredBy());
		assertTrue(report.report().courses().stream().anyMatch(course -> course.free()));
		assertTrue(report.report().courses().stream().anyMatch(course -> !course.free()));
		assertTrue(report.report().plan().stream().anyMatch(week ->
				week.steps().stream().anyMatch(step -> step.startsWith("Пройти курс:"))));
	}

	/**
	 * Развёрнутые ответы с проверкой и ограничениями дают «Продвинутый».
	 */
	@Test
	void strongAnswersReachAdvanced() {
		ChatResponse report = finish(new ChatService(new SessionStore()), STRONG);
		assertEquals("Продвинутый", report.report().level());
		assertEquals(15, report.report().score());
		assertEquals(4, report.report().plan().size());
		assertTrue(report.report().courses().isEmpty());
	}

	/**
	 * Повтор тех же ответов даёт тот же уровень и ту же сумму.
	 */
	@Test
	void sameAnswersProduceSameLevel() {
		ChatService first = new ChatService(new SessionStore());
		ChatService second = new ChatService(new SessionStore());
		ChatResponse one = finish(first, STRONG);
		ChatResponse two = finish(second, STRONG);
		assertEquals(one.report().level(), two.report().level());
		assertEquals(one.report().score(), two.report().score());
	}

	/**
	 * Зачёт в тренажёре поднимает балл темы и убирает её из пробелов.
	 */
	@Test
	void passedPracticeRaisesScoreAndClearsGap() {
		ChatService service = new ChatService(new SessionStore());
		ChatResponse report = finish(service, "не знаю");
		String gapId = report.report().answers().getFirst().questionId();
		ChatResponse task = service.chat(report.sessionId(), "", "practice", gapId);
		assertEquals("practice", task.phase());
		assertTrue(task.reply().contains("Тренажёр"));
		ChatResponse cleared = service.chat(report.sessionId(), STRONG);
		assertEquals("report", cleared.phase());
		assertEquals(gapId, cleared.clearedQuestionId());
		assertTrue(cleared.report().score() > report.report().score());
		assertTrue(cleared.report().answers().stream()
				.anyMatch(answer -> answer.questionId().equals(gapId) && answer.score() >= 2));
		assertTrue(cleared.report().answers().stream()
				.noneMatch(answer -> answer.questionId().equals(gapId) && answer.score() <= 1));
	}

	/**
	 * Слабая попытка оставляет пробел и балл на месте.
	 */
	@Test
	void weakPracticeKeepsTheGap() {
		ChatService service = new ChatService(new SessionStore());
		ChatResponse report = finish(service, "не знаю");
		String gapId = report.report().answers().getFirst().questionId();
		service.chat(report.sessionId(), "", "practice", gapId);
		ChatResponse failed = service.chat(report.sessionId(), "не знаю");
		assertEquals("practice", failed.phase());
		assertNull(failed.clearedQuestionId());
		assertEquals(report.report().score(), failed.report().score());
	}

	/**
	 * Непонятная роль не пускает дальше знакомства.
	 */
	@Test
	void unclearRoleAsksAgain() {
		ChatService service = new ChatService(new SessionStore());
		ChatResponse start = service.chat(null, "");
		ChatResponse again = service.chat(start.sessionId(), "привет");
		assertEquals("intro", again.phase());
		assertNull(again.role());
		assertTrue(again.reply().contains("аналитик или разработчик"));
	}

	/**
	 * Модель не назначает уровень: единицы в JSON не становятся продвинутым уровнем.
	 */
	@Test
	void modelScoresDoNotInflateLevel() {
		DialogueCoach coach = new DialogueCoach(
				new GigaChatProperties("client", "GIGACHAT_API_PERS", "test-key", "", "", "", 0, 200),
				heuristicAssessor,
				(system, user) -> {
					int start = user.indexOf("Фокус: ") + "Фокус: ".length();
					String id = user.substring(start, user.indexOf('\n', start)).trim();
					return "{\"scores\":[{\"id\":\"" + id + "\",\"score\":1,\"reason\":\"мало\"}],"
							+ "\"comment\":\"Выглядит очень сильно\",\"nextQuestion\":\"Уточни, как проверишь результат.\"}";
				},
				new CompetencyMap()
		);
		ChatService service = new ChatService(new SessionStore(), coach, PracticeCoach.heuristicOnly());
		ChatResponse report = finish(service, "не знаю");
		assertEquals("Базовый", report.report().level());
		assertEquals(5, report.report().score());
		assertEquals("gigachat", report.report().scoredBy());
		assertTrue(report.report().summary().contains("Выглядит очень сильно"));
		assertEquals(5, report.report().gaps().size());
	}

	/**
	 * Первые вопросы аналитика и разработчика расходятся по теме.
	 */
	@Test
	void rolesOpenDifferentCompetencies() {
		ChatService analysts = new ChatService(new SessionStore());
		ChatService developers = new ChatService(new SessionStore());
		String analyst = opening(analysts, "я аналитик");
		String developer = opening(developers, "я разработчик");
		assertTrue(analyst.contains("user story"));
		assertTrue(developer.contains("diff"));
		assertTrue(!analyst.equals(developer));
	}

	/**
	 * Проходит знакомство и отвечает одной и той же фразой до отчёта.
	 *
	 * @param service сервис диалога
	 * @param answer текст каждого ответа
	 * @return финальный ответ с отчётом
	 */
	private ChatResponse finish(ChatService service, String answer) {
		ChatResponse start = service.chat(null, "");
		String id = start.sessionId();
		service.chat(id, "я разработчик");
		service.chat(id, "Хочу принимать патчи от ИИ без регрессий");
		ChatResponse firstQuestion = service.chat(id, "Иногда прошу чат набросать черновик");
		assertEquals("dialogue", firstQuestion.phase());
		assertEquals(1, firstQuestion.questionNumber());
		boolean sawBonus = "bonus".equals(firstQuestion.phase());
		ChatResponse step = firstQuestion;
		for (int guard = 0; !"report".equals(step.phase()) && guard < 20; guard++) {
			step = service.chat(id, answer);
			if ("bonus".equals(step.phase()) || step.reply().contains("Дополнительное практическое")) {
				sawBonus = true;
			}
		}
		assertEquals("report", step.phase());
		assertTrue(sawBonus);
		return step;
	}

	/**
	 * Доводит сессию до первого вопроса диагностики.
	 *
	 * @param service сервис диалога
	 * @param role фраза с ролью
	 * @return текст первого вопроса
	 */
	private String opening(ChatService service, String role) {
		ChatResponse start = service.chat(null, "");
		service.chat(start.sessionId(), role);
		service.chat(start.sessionId(), "Хочу работать с ИИ аккуратнее");
		ChatResponse first = service.chat(start.sessionId(), "Пока почти не использую");
		assertEquals("dialogue", first.phase());
		return first.reply();
	}
}
