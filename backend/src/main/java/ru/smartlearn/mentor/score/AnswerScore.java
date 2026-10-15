package ru.smartlearn.mentor.score;

/**
 * Оценка одного ответа по рубрике от 0 до 3.
 *
 * @param questionId код задания
 * @param title короткая тема
 * @param score балл от 0 до 3
 * @param reason короткое обоснование
 */
public record AnswerScore(String questionId, String title, int score, String reason) {
}
