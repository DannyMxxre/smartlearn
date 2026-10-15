package ru.smartlearn.mentor.web;

import org.springframework.stereotype.Service;
import ru.smartlearn.mentor.dialog.EmptyMessageException;
import ru.smartlearn.mentor.dialog.MentorSession;
import ru.smartlearn.mentor.dialog.Phase;
import ru.smartlearn.mentor.dialog.Role;
import ru.smartlearn.mentor.dialog.SessionStore;
import ru.smartlearn.mentor.llm.AssessmentService;
import ru.smartlearn.mentor.question.Question;
import ru.smartlearn.mentor.question.QuestionBank;
import ru.smartlearn.mentor.question.RoleParser;
import ru.smartlearn.mentor.score.AnswerScore;
import ru.smartlearn.mentor.score.Assessment;
import ru.smartlearn.mentor.score.PlanWeek;
import ru.smartlearn.mentor.score.StoredAnswer;

import java.util.ArrayList;
import java.util.List;

/**
 * Ведёт обязательный сценарий: знакомство, диагностика, отчёт и план.
 * Переходы между шагами решает код, модель подключается только в конце.
 */
@Service
public class ChatService {

	private final SessionStore sessionStore;
	private final AssessmentService assessmentService;
	private final QuestionBank questionBank;

	/**
	 * Собирает сервис диалога.
	 *
	 * @param sessionStore хранилище сессий
	 * @param assessmentService подсчёт отчёта
	 * @param questionBank задания по ролям
	 */
	public ChatService(SessionStore sessionStore, AssessmentService assessmentService, QuestionBank questionBank) {
		this.sessionStore = sessionStore;
		this.assessmentService = assessmentService;
		this.questionBank = questionBank;
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
		MentorSession session = open(sessionId);
		synchronized (session) {
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
				case DIAGNOSIS -> onDiagnosis(session, text);
				case DONE -> "Диагностика уже завершена. Начни новую сессию, чтобы пройти её ещё раз.";
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
		session.setPhase(Phase.DIAGNOSIS);
		session.setQuestionIndex(0);
		List<Question> questions = questionBank.forRole(session.role());
		return questionText(questions.get(0), 0, questions.size());
	}

	/**
	 * Запоминает ответ и либо задаёт следующий вопрос, либо строит отчёт.
	 *
	 * @param session текущая сессия
	 * @param text ответ на текущее задание
	 * @return следующее задание или отчёт
	 */
	private String onDiagnosis(MentorSession session, String text) {
		List<Question> questions = questionBank.forRole(session.role());
		Question current = questions.get(session.questionIndex());
		session.addAnswer(new StoredAnswer(current.id(), current.title(), current.text(), text));
		int next = session.questionIndex() + 1;
		session.setQuestionIndex(next);
		if (next < questions.size()) {
			return questionText(questions.get(next), next, questions.size());
		}
		Assessment assessment = assessmentService.assess(session);
		session.complete(assessment);
		return render(assessment);
	}

	/**
	 * Формирует текст задания с номером.
	 *
	 * @param question задание
	 * @param index индекс с нуля
	 * @param total сколько заданий всего
	 * @return реплика наставника
	 */
	private String questionText(Question question, int index, int total) {
		return "Задание " + (index + 1) + " из " + total + ".\n\n" + question.text();
	}

	/**
	 * Собирает человекочитаемый отчёт и план.
	 *
	 * @param assessment результат оценки
	 * @return текст для ленты чата
	 */
	private String render(Assessment assessment) {
		StringBuilder text = new StringBuilder();
		text.append("Диагностика готова.\n\n");
		text.append("Уровень: ").append(assessment.level().title()).append('\n');
		text.append("Баллы: ").append(assessment.score()).append(" из ").append(assessment.maxScore()).append("\n\n");
		text.append(assessment.summary()).append("\n\n");
		text.append("Сильные стороны:\n");
		for (String strength : assessment.strengths()) {
			text.append("- ").append(strength).append('\n');
		}
		text.append("\nЧто подтянуть:\n");
		for (String gap : assessment.gaps()) {
			text.append("- ").append(gap).append('\n');
		}
		text.append("\nПлан на месяц:\n");
		for (PlanWeek week : assessment.plan()) {
			text.append("Неделя ").append(week.week()).append(". ").append(week.title()).append('\n');
			for (String step : week.steps()) {
				text.append("- ").append(step).append('\n');
			}
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
		List<Question> questions = session.role() == null ? List.of() : questionBank.forRole(session.role());
		Integer questionNumber = session.phase() == Phase.DIAGNOSIS ? session.questionIndex() + 1 : null;
		Integer questionCount = questions.isEmpty() ? null : questions.size();
		return new ChatResponse(
				session.id(),
				session.phase().wireName(),
				reply,
				roleName(session.role()),
				questionNumber,
				questionCount,
				report(session.assessment())
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
		return new ReportResponse(
				assessment.level().title(),
				assessment.score(),
				assessment.maxScore(),
				assessment.strengths(),
				assessment.gaps(),
				assessment.summary(),
				List.copyOf(plan),
				List.copyOf(answers),
				assessment.scoredBy()
		);
	}
}
