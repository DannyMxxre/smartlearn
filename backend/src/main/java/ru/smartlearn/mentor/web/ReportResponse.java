package ru.smartlearn.mentor.web;

import java.util.List;

/**
 * Отчёт, который интерфейс рисует карточкой в конце диагностики.
 *
 * @param level подпись уровня
 * @param score набранные баллы
 * @param maxScore максимум баллов
 * @param strengths сильные стороны
 * @param gaps пробелы
 * @param summary короткий вывод
 * @param plan четыре недели
 * @param answers баллы по заданиям
 * @param scoredBy {@code yandex} или {@code heuristic}
 * @param courses курсы под слабые темы
 */
public record ReportResponse(
		String level,
		int score,
		int maxScore,
		List<String> strengths,
		List<String> gaps,
		String summary,
		List<PlanWeekResponse> plan,
		List<AnswerScoreResponse> answers,
		String scoredBy,
		List<CourseResponse> courses
) {
}
