package ru.smartlearn.mentor.score;

import ru.smartlearn.mentor.dialog.Level;
import ru.smartlearn.mentor.dialog.Role;

import java.util.List;

/**
 * Готовый результат диагностики.
 * Уровень внутри уже посчитан кодом по сумме баллов.
 *
 * @param level уровень по шкале
 * @param score набранная сумма
 * @param maxScore максимум, сейчас 12
 * @param answers оценки по заданиям
 * @param strengths сильные стороны
 * @param gaps пробелы
 * @param summary короткий вывод
 * @param plan шаги на четыре недели
 * @param scoredBy {@code yandex} или {@code heuristic}
 * @param courses курсы под слабые темы
 */
public record Assessment(
		Level level,
		int score,
		int maxScore,
		List<AnswerScore> answers,
		List<String> strengths,
		List<String> gaps,
		String summary,
		List<PlanWeek> plan,
		String scoredBy,
		List<Course> courses
) {
}
