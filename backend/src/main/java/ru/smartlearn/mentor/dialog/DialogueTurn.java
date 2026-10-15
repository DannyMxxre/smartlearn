package ru.smartlearn.mentor.dialog;

/**
 * Одна реплика в истории сессии.
 *
 * @param author {@code user} или {@code assistant}
 * @param text текст реплики
 */
public record DialogueTurn(String author, String text) {
}
