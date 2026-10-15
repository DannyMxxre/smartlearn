package ru.smartlearn.mentor.question;

/**
 * Задание диагностики для одной роли.
 *
 * @param id стабильный код задания
 * @param title короткая тема для отчёта
 * @param text формулировка, которую видит человек
 * @param focus на что смотреть при оценке ответа
 */
public record Question(String id, String title, String text, String focus) {
}
