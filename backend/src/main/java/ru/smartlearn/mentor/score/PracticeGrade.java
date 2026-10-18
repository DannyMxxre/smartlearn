package ru.smartlearn.mentor.score;

/**
 * Оценка одной попытки в тренажёре.
 *
 * @param score балл от 0 до 3
 * @param passed {@code true}, если балл не ниже 2
 * @param feedback короткий разбор для человека
 * @param scoredBy {@code gigachat} или {@code heuristic}
 */
public record PracticeGrade(int score, boolean passed, String feedback, String scoredBy) {
}
