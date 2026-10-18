package ru.smartlearn.mentor.score;

/**
 * Одна компетенция карты для выбранной роли.
 *
 * @param id устойчивый код, по нему цепляются курсы и тренажёр
 * @param title короткое название для отчёта
 * @param prompt вопрос, с которого наставник открывает тему
 * @param criteria критерии баллов 0–3 для модели
 */
public record Competency(String id, String title, String prompt, String criteria) {
}
