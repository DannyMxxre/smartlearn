package ru.smartlearn.mentor.web;

/**
 * Входящее сообщение чата.
 *
 * @param sessionId идентификатор сессии, пустой для начала диалога
 * @param message текст человека, пустой только для приветствия
 */
public record ChatRequest(String sessionId, String message) {
}
