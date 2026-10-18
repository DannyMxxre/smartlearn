package ru.smartlearn.mentor.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.smartlearn.mentor.dialog.EmptyMessageException;
import ru.smartlearn.mentor.dialog.MentorSession;
import ru.smartlearn.mentor.dialog.Phase;
import ru.smartlearn.mentor.dialog.Role;
import ru.smartlearn.mentor.dialog.SessionStore;
import ru.smartlearn.mentor.llm.DialogueCoach;
import ru.smartlearn.mentor.llm.PracticeCoach;
import ru.smartlearn.mentor.question.RoleParser;
import ru.smartlearn.mentor.score.AnswerScore;
import ru.smartlearn.mentor.score.Assessment;
import ru.smartlearn.mentor.score.AssessmentComposer;
import ru.smartlearn.mentor.score.Course;
import ru.smartlearn.mentor.score.PlanTemplates;
import ru.smartlearn.mentor.score.PlanWeek;
import ru.smartlearn.mentor.score.PracticeGrade;

import java.util.ArrayList;
import java.util.List;

/**
 * Ведёт обязательный сценарий: знакомство, открытый диалог, практика, отчёт.
 * Переходы между шагами решает код. Модель ставит баллы по компетенциям.
 */
@Service
public class ChatService {

	private final SessionStore sessionStore;
	private final DialogueCoach dialogueCoach;
	private final PracticeCoach practiceCoach;
	private final AssessmentComposer composer = new AssessmentComposer();

	/**
	 * Собирает сервис диалога.
	 *
	 * @param sessionStore хранилище сессий
	 * @param dialogueCoach открытый диалог и бонусные кейсы
	 * @param practiceCoach тренажёр по пробелам
	 */
	@Autowired
	public ChatService(SessionStore sessionStore, DialogueCoach dialogueCoach, PracticeCoach practiceCoach) {
		this.sessionStore = sessionStore;
		this.dialogueCoach = dialogueCoach;
		this.practiceCoach = practiceCoach;
	}

	/**
	 * Собирает сервис для тестов без облака.
	 *
	 * @param sessionStore хранилище сессий
	 */
	public ChatService(SessionStore sessionStore) {
		this(sessionStore, DialogueCoach.heuristicOnly(), PracticeCoach.heuristicOnly());
	}

	/**
	 * Принимает реплику и возвращает следующий шаг сценария.
	 * Пустое первое сообщение только открывает сессию и здоровается.
	 *
	 * @param sessionId идентификатор или пусто для новой сессии
	 * @param message текст человека
	 * @return реплика наставника и состояние
	 */
	public ChatResponse chat(String sessionId, String message) {
		return chat(sessionId, message, null, null);
	}

	/**
	 * Принимает реплику, старт тренажёра или ответ на практику.
	 *
	 * @param sessionId идентификатор или пусто для новой сессии
	 * @param message текст человека
	 * @param action {@code practice}, чтобы открыть тренажёр
	 * @param questionId код пробела
	 * @return реплика наставника и состояние
	 */
	public ChatResponse chat(String sessionId, String message, String action, String questionId) {
		MentorSession session = open(sessionId);
		synchronized (session) {
			if ("practice".equals(action)) {
				String reply = startPractice(session, questionId);
				session.addTurn("assistant", reply);
				return respond(session, reply);
			}
			String text = message == null ? "" : message.trim();
			if (text.isEmpty()) {
				if (session.turns().isEmpty()) {
					String reply = greeting();
					session.addTurn("assistant", reply);
					return respond(session, reply);
				}
				throw new EmptyMessageException();
			}
			session.addTurn("user", text);
			String reply = switch (session.phase()) {
				case ASK_ROLE -> onRole(session, text);
				case ASK_GOAL -> onGoal(session, text);
				case ASK_SKILLS -> onSkills(session, text);
				case DIALOGUE, BONUS -> continueDialogue(session, text);
				case PRACTICE -> onPractice(session, text);
				case DONE -> "Диагностика уже завершена. Закрой пробел кнопкой «Отработать» в боковой панели или начни новую сессию.";
			};
			session.addTurn("assistant", reply);
			return respond(session, reply);
		}
	}

	/**
	 * Открывает существующую сессию или создаёт новую.
	 *
	 * @param sessionId идентификатор из клиента
	 * @return сессия для этого запроса
	 */
	private MentorSession open(String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			return sessionStore.create();
		}
		return sessionStore.require(sessionId);
	}

	/**
	 * Принимает роль или просит назвать её ещё раз.
	 *
	 * @param session текущая сессия
	 * @param text сообщение человека
	 * @return следующий вопрос
	 */
	private String onRole(MentorSession session, String text) {
		return RoleParser.parse(text)
				.map(role -> {
					session.setRole(role);
					session.setPhase(Phase.ASK_GOAL);
					return "Роль принял: " + role.title() + ". Какая цель на ближайший месяц? Например: быстрее разбирать требования или писать код с ИИ и не терять качество.";
				})
				.orElse("Нужна одна роль: аналитик или разработчик.");
	}

	/**
	 * Сохраняет цель и спрашивает про текущий опыт.
	 *
	 * @param session текущая сессия
	 * @param text цель на месяц
	 * @return вопрос про навыки
	 */
	private String onGoal(MentorSession session, String text) {
		session.setGoal(text);
		session.setPhase(Phase.ASK_SKILLS);
		return "Как сейчас используешь ИИ в работе? Назови инструменты и типичные задачи. Если почти не используешь — так и напиши.";
	}

	/**
	 * Сохраняет опыт и выдаёт первое задание выбранной роли.
	 *
	 * @param session текущая сессия
	 * @param text описание опыта
	 * @return первое задание
	 */
	private String onSkills(MentorSession session, String text) {
		session.setSkills(text);
		return dialogueCoach.open(session);
	}

	/**
	 * Принимает ответ диалога или бонусного кейса.
	 * Пустой ответ наставника означает, что отчёт уже собран.
	 *
	 * @param session сессия диагностики
	 * @param text ответ человека
	 * @return следующий вопрос или текст отчёта
	 */
	private String continueDialogue(MentorSession session, String text) {
		String next = dialogueCoach.answer(session, text);
		if (next == null) {
			return render(session.assessment());
		}
		return next;
	}

	/**
	 * Открывает тренажёр по одному ещё не закрытому пробелу.
	 *
	 * @param session сессия с отчётом
	 * @param questionId код темы
	 * @return текст задания
	 */
	private String startPractice(MentorSession session, String questionId) {
		if (session.assessment() == null) {
			return "Сначала закончи диагностику. Тренажёр открывается по пробелам из отчёта.";
		}
		AnswerScore gap = findScore(session, questionId);
		if (gap == null) {
			return "Такой темы в отчёте нет.";
		}
		if (gap.score() > 1) {
			return "Тема «" + gap.title() + "» уже закрыта.";
		}
		String task = practiceCoach.taskFor(questionId);
		if (task.isBlank()) {
			return "Для этой темы нет упражнения.";
		}
		session.setPhase(Phase.PRACTICE);
		session.setPracticeQuestionId(questionId);
		return "### Тренажёр: " + gap.title() + "\n\n" + task + "\n\nОтвет зачтётся, если в нём будут конкретные действия и проверка результата.";
	}

	/**
	 * Принимает попытку и при зачёте поднимает балл темы.
	 *
	 * @param session сессия в тренажёре
	 * @param text ответ человека
	 * @return разбор попытки
	 */
	private String onPractice(MentorSession session, String text) {
		AnswerScore gap = findScore(session, session.practiceQuestionId());
		if (gap == null) {
			session.setPhase(Phase.DONE);
			return "Тренажёр сбился. Вернись к отчёту и выбери пробел ещё раз.";
		}
		String task = practiceCoach.taskFor(gap.questionId());
		PracticeGrade grade = practiceCoach.grade(session.role(), gap.title(), task, text);
		if (!grade.passed()) {
			return "### Пока не зачёт\n\n" + grade.feedback() + "\n\nДопиши ответ: нужны действия и способ проверить качество. Пробел пока остаётся.";
		}
		int raised = Math.max(gap.score(), Math.max(2, grade.score()));
		raiseScore(session, gap.questionId(), raised, grade.feedback());
		session.setPhase(Phase.DONE);
		session.setPracticeQuestionId(null);
		session.markCleared(gap.questionId());
		return "### Зачёт\n\n" + grade.feedback() + "\n\nБалл по теме «" + gap.title() + "» вырос до **" + raised + "**. Пробел закрыт.";
	}

	/**
	 * Ищет оценку темы в текущем отчёте.
	 *
	 * @param session сессия с отчётом
	 * @param questionId код задания
	 * @return оценка или {@code null}
	 */
	private AnswerScore findScore(MentorSession session, String questionId) {
		if (session.assessment() == null || questionId == null) {
			return null;
		}
		for (AnswerScore score : session.assessment().answers()) {
			if (score.questionId().equals(questionId)) {
				return score;
			}
		}
		return null;
	}

	/**
	 * Поднимает балл одной темы и заново собирает уровень, пробелы и курсы.
	 *
	 * @param session сессия с отчётом
	 * @param questionId тема
	 * @param raised новый балл
	 * @param feedback обоснование зачёта
	 */
	private void raiseScore(MentorSession session, String questionId, int raised, String feedback) {
		Assessment current = session.assessment();
		ArrayList<AnswerScore> scores = new ArrayList<>();
		for (AnswerScore score : current.answers()) {
			if (score.questionId().equals(questionId)) {
				scores.add(new AnswerScore(score.questionId(), score.title(), raised, feedback));
			} else {
				scores.add(score);
			}
		}
		session.replaceAssessment(composer.compose(
				session.role(),
				scores,
				List.of(),
				List.of(),
				"",
				PlanTemplates.forRole(session.role()),
				current.scoredBy()
		));
	}

	/**
	 * Собирает отчёт в Markdown, чтобы чат показал заголовки, списки и ссылки.
	 *
	 * @param assessment результат оценки
	 * @return текст для ленты чата
	 */
	private String render(Assessment assessment) {
		StringBuilder text = new StringBuilder();
		text.append("Диагностика готова.\n\n");
		text.append("**Уровень:** ").append(assessment.level().title()).append("  \n");
		text.append("**Баллы:** ").append(assessment.score()).append(" из ").append(assessment.maxScore()).append("\n\n");
		text.append(assessment.summary()).append("\n\n");
		text.append("### Сильные стороны\n");
		for (String strength : assessment.strengths()) {
			text.append("- ").append(strength).append('\n');
		}
		text.append("\n### Что подтянуть\n");
		for (String gap : assessment.gaps()) {
			text.append("- ").append(gap).append('\n');
		}
		text.append("\n### План на месяц\n");
		for (PlanWeek week : assessment.plan()) {
			text.append("\n**Неделя ").append(week.week()).append(". ").append(week.title()).append("**\n");
			for (String step : week.steps()) {
				text.append("- ").append(step).append('\n');
			}
		}
		text.append("\n### Курсы\n");
		if (assessment.courses().isEmpty()) {
			text.append("- Явных пробелов нет, отдельные курсы не нужны.\n");
		}
		for (Course course : assessment.courses()) {
			text.append("- **").append(course.free() ? "Бесплатно" : "Платно").append(".** ");
			text.append("[").append(course.title()).append("](").append(course.url()).append(")");
			text.append(" — ").append(course.provider()).append(", неделя ").append(course.week()).append('\n');
		}
		if ("heuristic".equals(assessment.scoredBy())) {
			text.append("\nОценка посчитана локально, без YandexGPT. Уровень выведен из баллов.");
		} else {
			text.append("\nОбоснование подготовил YandexGPT. Уровень выведен из баллов в коде.");
		}
		return text.toString().trim();
	}

	/**
	 * Приветствует и просит выбрать роль.
	 *
	 * @return первая реплика наставника
	 */
	private String greeting() {
		return "Привет. Я наставник по ИИ-инструментам. За несколько минут пойму, как ты ими пользуешься, и соберу план на месяц.\n\nКто ты по роли: аналитик или разработчик?";
	}

	/**
	 * Упаковывает состояние сессии в ответ API.
	 *
	 * @param session сессия после обработки реплики
	 * @param reply текст, который только что сказал наставник
	 * @return ответ клиенту
	 */
	private ChatResponse respond(MentorSession session, String reply) {
		Integer questionNumber = null;
		Integer questionCount = null;
		if (session.phase() == Phase.DIALOGUE) {
			questionNumber = session.substantiveReplies() + 1;
		}
		if (session.phase() == Phase.BONUS) {
			questionNumber = session.bonusIndex() + 1;
			questionCount = 2;
		}
		return new ChatResponse(
				session.id(),
				session.phase().wireName(),
				reply,
				roleName(session.role()),
				questionNumber,
				questionCount,
				report(session.assessment()),
				session.consumeCleared()
		);
	}

	/**
	 * Переводит роль в поле API.
	 *
	 * @param role роль или {@code null}
	 * @return {@code analyst}, {@code developer} или {@code null}
	 */
	private String roleName(Role role) {
		if (role == null) {
			return null;
		}
		return role == Role.ANALYST ? "analyst" : "developer";
	}

	/**
	 * Переводит отчёт в контракт API.
	 *
	 * @param assessment отчёт или {@code null}
	 * @return карточка или {@code null}, пока диагностики нет
	 */
	private ReportResponse report(Assessment assessment) {
		if (assessment == null) {
			return null;
		}
		List<PlanWeekResponse> plan = new ArrayList<>();
		for (PlanWeek week : assessment.plan()) {
			plan.add(new PlanWeekResponse(week.week(), week.title(), week.steps()));
		}
		List<AnswerScoreResponse> answers = new ArrayList<>();
		for (AnswerScore score : assessment.answers()) {
			answers.add(new AnswerScoreResponse(score.questionId(), score.title(), score.score(), score.reason()));
		}
		List<CourseResponse> courses = new ArrayList<>();
		for (Course course : assessment.courses()) {
			courses.add(new CourseResponse(
					course.questionId(),
					course.gapTitle(),
					course.title(),
					course.provider(),
					course.free(),
					course.url(),
					course.week()
			));
		}
		return new ReportResponse(
				assessment.level().title(),
				assessment.score(),
				assessment.maxScore(),
				assessment.strengths(),
				assessment.gaps(),
				assessment.summary(),
				List.copyOf(plan),
				List.copyOf(answers),
				assessment.scoredBy(),
				List.copyOf(courses)
		);
	}
}
