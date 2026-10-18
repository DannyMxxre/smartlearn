package ru.smartlearn.mentor.score;

/**
 * Разбор одной попытки в тренажёре.
 *
 * @param score балл от 0 до 3
 * @param mastered {@code true}, только если балл равен 3
 * @param whyNotMax почему ответ не дотянул до максимума, пусто при тройке
 * @param techniques короткие приёмы, которых не хватило, пусто при тройке
 * @param nextTask похожее задание для проверки, пусто при тройке
 * @param scoredBy {@code gigachat} или {@code heuristic}
 */
public record PracticeGrade(
		int score,
		boolean mastered,
		String whyNotMax,
		String techniques,
		String nextTask,
		String scoredBy
) {
}
