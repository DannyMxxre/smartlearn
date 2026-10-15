package ru.smartlearn.mentor.web;

/**
 * Ответ одного шага диалога.
 *
 * @param sessionId идентификатор, который нужно присылать дальше
 * @param phase {@code intro}, {@code diagnosis} или {@code report}
 * @param reply реплика наставника
 * @param role {@code analyst}, {@code developer} или {@code null}
 * @param questionNumber номер текущего задания, с 1
 * @param questionCount сколько заданий в диагностике
 * @param report карточка отчёта, когда она уже есть
 * @param clearedQuestionId пробел, который только что закрыли и нужно убрать с анимацией
 */
public record ChatResponse(
		String sessionId,
		String phase,
		String reply,
		String role,
		Integer questionNumber,
		Integer questionCount,
		ReportResponse report,
		String clearedQuestionId
) {
}
