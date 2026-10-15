package ru.smartlearn.mentor.web;

/**
 * Балл одного задания в ответе API.
 *
 * @param questionId код задания
 * @param title тема
 * @param score балл от 0 до 3
 * @param reason обоснование
 */
public record AnswerScoreResponse(String questionId, String title, int score, String reason) {
}
