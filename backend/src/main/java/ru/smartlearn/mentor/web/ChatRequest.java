package ru.smartlearn.mentor.web;

/**
 * Входящее сообщение чата.
 *
 * @param sessionId идентификатор сессии, пустой для начала диалога
 * @param message текст человека, пустой только для приветствия или старта тренажёра
 * @param action {@code practice}, если нужно открыть тренажёр по пробелу
 * @param questionId код пробела для тренажёра
 */
public record ChatRequest(String sessionId, String message, String action, String questionId) {
}
