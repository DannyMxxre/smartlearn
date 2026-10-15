package ru.smartlearn.mentor.web;

import org.junit.jupiter.api.Test;
import ru.smartlearn.mentor.dialog.Level;
import ru.smartlearn.mentor.dialog.MentorSession;
import ru.smartlearn.mentor.dialog.Role;
import ru.smartlearn.mentor.dialog.SessionStore;
import ru.smartlearn.mentor.llm.AssessmentService;
import ru.smartlearn.mentor.llm.LlmAssessor;
import ru.smartlearn.mentor.question.QuestionBank;
import ru.smartlearn.mentor.score.Assessment;
import ru.smartlearn.mentor.score.HeuristicAssessor;
import ru.smartlearn.mentor.score.StoredAnswer;

import java.util.List;

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

	private final QuestionBank questionBank = new QuestionBank();
	private final HeuristicAssessor heuristicAssessor = new HeuristicAssessor();

	/**
	 * Слабые отписки дают уровень «Новичок».
	 */
	@Test
	void weakAnswersStayNovice() {
		ChatResponse report = finish(new ChatService(new SessionStore(), assessor(), questionBank), "не знаю");
		assertEquals("Новичок", report.report().level());
		assertEquals(0, report.report().score());
		assertEquals("heuristic", report.report().scoredBy());
	}

	/**
	 * Развёрнутые ответы с проверкой и ограничениями дают «Продвинутый».
	 */
	@Test
	void strongAnswersReachAdvanced() {
		ChatResponse report = finish(new ChatService(new SessionStore(), assessor(), questionBank), STRONG);
		assertEquals("Продвинутый", report.report().level());
		assertEquals(12, report.report().score());
		assertEquals(4, report.report().plan().size());
	}

	/**
	 * Повтор тех же ответов даёт тот же уровень и ту же сумму.
	 */
	@Test
	void sameAnswersProduceSameLevel() {
		ChatService first = new ChatService(new SessionStore(), assessor(), questionBank);
		ChatService second = new ChatService(new SessionStore(), assessor(), questionBank);
		ChatResponse one = finish(first, STRONG);
		ChatResponse two = finish(second, STRONG);
		assertEquals(one.report().level(), two.report().level());
		assertEquals(one.report().score(), two.report().score());
	}

	/**
	 * Непонятная роль не пускает дальше знакомства.
	 */
	@Test
	void unclearRoleAsksAgain() {
		ChatService service = new ChatService(new SessionStore(), assessor(), questionBank);
		ChatResponse start = service.chat(null, "");
		ChatResponse again = service.chat(start.sessionId(), "привет");
		assertEquals("intro", again.phase());
		assertNull(again.role());
		assertTrue(again.reply().contains("аналитик или разработчик"));
	}

	/**
	 * Модель не назначает уровень: низкие баллы в JSON остаются базовым уровнем.
	 */
	@Test
	void modelScoresDoNotInflateLevel() {
		LlmAssessor assessor = new LlmAssessor((system, user) -> """
				{"scores":[
				{"id":"d-bug","score":1,"reason":"мало"},
				{"id":"d-api","score":1,"reason":"мало"},
				{"id":"d-practice","score":1,"reason":"мало"},
				{"id":"d-secrets","score":1,"reason":"мало"}
				],"strengths":["Смелость"],"gaps":["Проверка"],"summary":"Выглядит очень сильно","plan":[
				{"week":1,"title":"А","steps":["шаг"]},
				{"week":2,"title":"Б","steps":["шаг"]},
				{"week":3,"title":"В","steps":["шаг"]},
				{"week":4,"title":"Г","steps":["шаг"]}
				]}
				""", questionBank);
		MentorSession session = new MentorSession("test");
		session.setRole(Role.DEVELOPER);
		session.setGoal("цель");
		session.setSkills("навыки");
		for (var question : questionBank.forRole(Role.DEVELOPER)) {
			session.addAnswer(new StoredAnswer(question.id(), question.title(), question.text(), "не знаю"));
		}
		Assessment assessment = assessor.assess(session);
		assertEquals(Level.BASIC, assessment.level());
		assertEquals(4, assessment.score());
		assertEquals("yandex", assessment.scoredBy());
		assertEquals(List.of("Смелость"), assessment.strengths());
	}

	/**
	 * Проходит знакомство разработчика и четыре одинаковых ответа.
	 *
	 * @param service сервис с локальной оценкой
	 * @param answer текст каждого задания
	 * @return финальный ответ с отчётом
	 */
	private ChatResponse finish(ChatService service, String answer) {
		ChatResponse start = service.chat(null, "");
		String id = start.sessionId();
		service.chat(id, "я разработчик");
		service.chat(id, "Хочу принимать патчи от ИИ без регрессий");
		ChatResponse firstQuestion = service.chat(id, "Иногда прошу чат набросать черновик");
		assertEquals("diagnosis", firstQuestion.phase());
		assertEquals(1, firstQuestion.questionNumber());
		service.chat(id, answer);
		service.chat(id, answer);
		service.chat(id, answer);
		ChatResponse report = service.chat(id, answer);
		assertEquals("report", report.phase());
		return report;
	}

	/**
	 * Подменяет облако локальным разборщиком, ключ в тесте не нужен.
	 *
	 * @return сервис оценки без YandexGPT
	 */
	private AssessmentService assessor() {
		return new AssessmentService(
				new ru.smartlearn.mentor.config.YandexProperties("", "", "", "", "Api-Key", 0, 1800),
				new LlmAssessor((system, user) -> "{}", questionBank),
				heuristicAssessor
		);
	}
}
