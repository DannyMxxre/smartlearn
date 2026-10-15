package ru.smartlearn.mentor.score;

/**
 * Ответ человека вместе с заданием, на которое он отвечал.
 *
 * @param questionId код задания
 * @param title короткая тема
 * @param question полный текст задания
 * @param answer текст ответа
 */
public record StoredAnswer(String questionId, String title, String question, String answer) {
}
