package ru.smartlearn.mentor.web;

/**
 * Состояние внешних зависимостей, которое можно показать в интерфейсе.
 * Секреты сюда не попадают.
 *
 * @param yandexConnected заданы ли ключ и каталог YandexGPT
 */
public record StatusResponse(boolean yandexConnected) {
}
