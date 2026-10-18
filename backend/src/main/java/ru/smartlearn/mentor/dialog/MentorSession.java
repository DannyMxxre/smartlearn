package ru.smartlearn.mentor.dialog;

import ru.smartlearn.mentor.score.AnswerScore;
import ru.smartlearn.mentor.score.Assessment;
import ru.smartlearn.mentor.score.StoredAnswer;

import java.util.ArrayList;
import java.util.List;

/**
 * Один проход диагностики в памяти процесса.
 * После перезапуска сервера сессия исчезает.
 */
public final class MentorSession {

	private final String id;
	private final List<DialogueTurn> turns = new ArrayList<>();
	private final List<StoredAnswer> answers = new ArrayList<>();
	private Phase phase = Phase.ASK_ROLE;
	private Role role;
	private String goal = "";
	private String skills = "";
	private int questionIndex;
	private int competencyIndex;
	private int substantiveReplies;
	private int bonusIndex = -1;
	private boolean followUp;
	private String focusId;
	private String summaryComment = "";
	private String scoredBy;
	private final List<AnswerScore> observations = new ArrayList<>();
	private Assessment assessment;
	private String practiceQuestionId;
	private String clearedQuestionId;

	/**
	 * Создаёт пустую сессию с уже выданным идентификатором.
	 *
	 * @param id идентификатор сессии
	 */
	public MentorSession(String id) {
		this.id = id;
	}

	/**
	 * Возвращает идентификатор сессии.
	 *
	 * @return id для следующих запросов
	 */
	public String id() {
		return id;
	}

	/**
	 * Возвращает текущий шаг сценария.
	 *
	 * @return фаза диалога
	 */
	public Phase phase() {
		return phase;
	}

	/**
	 * Возвращает выбранную роль.
	 *
	 * @return роль или {@code null}, пока её нет
	 */
	public Role role() {
		return role;
	}

	/**
	 * Возвращает цель на месяц.
	 *
	 * @return текст цели
	 */
	public String goal() {
		return goal;
	}

	/**
	 * Возвращает описание текущего опыта.
	 *
	 * @return текст про навыки
	 */
	public String skills() {
		return skills;
	}

	/**
	 * Возвращает номер текущего задания, начиная с нуля.
	 *
	 * @return индекс в банке вопросов роли
	 */
	public int questionIndex() {
		return questionIndex;
	}

	/**
	 * Возвращает уже данные ответы.
	 *
	 * @return неизменяемая копия ответов
	 */
	public List<StoredAnswer> answers() {
		return List.copyOf(answers);
	}

	/**
	 * Возвращает готовый отчёт.
	 *
	 * @return отчёт или {@code null}, пока диагностика не закончена
	 */
	public Assessment assessment() {
		return assessment;
	}

	/**
	 * Возвращает реплики диалога.
	 *
	 * @return неизменяемая копия реплик
	 */
	public List<DialogueTurn> turns() {
		return List.copyOf(turns);
	}

	/**
	 * Запоминает реплику пользователя или наставника.
	 *
	 * @param author {@code user} или {@code assistant}
	 * @param text текст реплики
	 */
	public void addTurn(String author, String text) {
		turns.add(new DialogueTurn(author, text));
	}

	/**
	 * Переводит сессию на следующий шаг сценария.
	 *
	 * @param phase новая фаза
	 */
	public void setPhase(Phase phase) {
		this.phase = phase;
	}

	/**
	 * Фиксирует роль после знакомства.
	 *
	 * @param role выбранная роль
	 */
	public void setRole(Role role) {
		this.role = role;
	}

	/**
	 * Сохраняет цель на месяц.
	 *
	 * @param goal текст цели
	 */
	public void setGoal(String goal) {
		this.goal = goal;
	}

	/**
	 * Сохраняет описание текущего опыта.
	 *
	 * @param skills текст про навыки
	 */
	public void setSkills(String skills) {
		this.skills = skills;
	}

	/**
	 * Сдвигает указатель на следующее задание.
	 *
	 * @param questionIndex новый индекс
	 */
	public void setQuestionIndex(int questionIndex) {
		this.questionIndex = questionIndex;
	}

	/**
	 * Запоминает, какая компетенция сейчас в фокусе.
	 *
	 * @param focusId код компетенции
	 */
	public void setFocusId(String focusId) {
		this.focusId = focusId;
	}

	/**
	 * Возвращает компетенцию текущего вопроса.
	 *
	 * @return код или {@code null}
	 */
	public String focusId() {
		return focusId;
	}

	/**
	 * Сдвигает указатель карты компетенций.
	 *
	 * @param competencyIndex индекс следующей темы
	 */
	public void setCompetencyIndex(int competencyIndex) {
		this.competencyIndex = competencyIndex;
	}

	/**
	 * Возвращает индекс текущей темы в карте.
	 *
	 * @return индекс с нуля
	 */
	public int competencyIndex() {
		return competencyIndex;
	}

	/**
	 * Отмечает, что следующий ответ всё ещё уточняет ту же тему.
	 *
	 * @param followUp {@code true}, если ждём уточнение
	 */
	public void setFollowUp(boolean followUp) {
		this.followUp = followUp;
	}

	/**
	 * Проверяет, ждём ли уточнение той же темы.
	 *
	 * @return {@code true}, если тема не сменилась
	 */
	public boolean followUp() {
		return followUp;
	}

	/**
	 * Увеличивает счётчик содержательных реплик диалога.
	 */
	public void incrementReplies() {
		this.substantiveReplies++;
	}

	/**
	 * Возвращает, сколько содержательных ответов уже было в диалоге.
	 *
	 * @return число реплик, без бонусных кейсов
	 */
	public int substantiveReplies() {
		return substantiveReplies;
	}

	/**
	 * Запоминает номер бонусного кейса.
	 *
	 * @param bonusIndex индекс с нуля или {@code -1} до практики
	 */
	public void setBonusIndex(int bonusIndex) {
		this.bonusIndex = bonusIndex;
	}

	/**
	 * Возвращает номер текущего бонусного кейса.
	 *
	 * @return индекс с нуля
	 */
	public int bonusIndex() {
		return bonusIndex;
	}

	/**
	 * Добавляет одну оценку реплики до свёртки в компетенцию.
	 *
	 * @param observation балл по коду компетенции
	 */
	public void addObservation(AnswerScore observation) {
		observations.add(observation);
	}

	/**
	 * Возвращает все оценки реплик.
	 *
	 * @return неизменяемая копия
	 */
	public List<AnswerScore> observations() {
		return List.copyOf(observations);
	}

	/**
	 * Запоминает комментарий модели для итогового абзаца.
	 *
	 * @param summaryComment текст комментария
	 */
	public void setSummaryComment(String summaryComment) {
		this.summaryComment = summaryComment;
	}

	/**
	 * Возвращает последний содержательный комментарий.
	 *
	 * @return текст или пустая строка
	 */
	public String summaryComment() {
		return summaryComment;
	}

	/**
	 * Запоминает, кто ставил баллы.
	 * Любой локальный пересчёт помечает весь отчёт как локальный.
	 *
	 * @param source {@code gigachat} или {@code heuristic}
	 */
	public void markScoredBy(String source) {
		if (scoredBy == null || "heuristic".equals(source)) {
			scoredBy = source;
		}
	}

	/**
	 * Возвращает источник баллов.
	 *
	 * @return {@code gigachat} или {@code heuristic}
	 */
	public String scoredBy() {
		return scoredBy == null ? "heuristic" : scoredBy;
	}

	/**
	 * Добавляет ответ на задание.
	 *
	 * @param answer ответ вместе с текстом задания
	 */
	public void addAnswer(StoredAnswer answer) {
		answers.add(answer);
	}

	/**
	 * Закрывает диагностику готовым отчётом.
	 *
	 * @param assessment посчитанный отчёт
	 */
	public void complete(Assessment assessment) {
		this.assessment = assessment;
		this.phase = Phase.DONE;
	}

	/**
	 * Подменяет отчёт после тренажёра, не меняя шаг сценария.
	 *
	 * @param assessment пересчитанный отчёт
	 */
	public void replaceAssessment(Assessment assessment) {
		this.assessment = assessment;
	}

	/**
	 * Запоминает тему, которую человек сейчас отрабатывает.
	 *
	 * @param practiceQuestionId код задания или {@code null}
	 */
	public void setPracticeQuestionId(String practiceQuestionId) {
		this.practiceQuestionId = practiceQuestionId;
	}

	/**
	 * Возвращает тему текущего тренажёра.
	 *
	 * @return код задания или {@code null}
	 */
	public String practiceQuestionId() {
		return practiceQuestionId;
	}

	/**
	 * Помечает пробел, который только что закрыли.
	 * Интерфейс прочитает это один раз и проиграет анимацию.
	 *
	 * @param questionId код закрытого задания
	 */
	public void markCleared(String questionId) {
		this.clearedQuestionId = questionId;
	}

	/**
	 * Отдаёт и забывает код закрытого пробела.
	 *
	 * @return код задания или {@code null}
	 */
	public String consumeCleared() {
		String questionId = clearedQuestionId;
		clearedQuestionId = null;
		return questionId;
	}
}
