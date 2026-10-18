package ru.smartlearn.mentor.web;

/**
 * Состояние внешних зависимостей, которое можно показать в интерфейсе.
 * Секреты сюда не попадают.
 *
 * @param gigachatConnected заданы ли Client ID, Scope и ключ авторизации GigaChat
 */
public record StatusResponse(boolean gigachatConnected) {
}
