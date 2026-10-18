package ru.smartlearn.mentor.score;

/**
 * Практический кейс в конце открытого диалога.
 *
 * @param id код кейса
 * @param competencyId компетенция, в которую падает балл
 * @param title название для отчёта
 * @param text формулировка задания
 */
public record BonusTask(String id, String competencyId, String title, String text) {
}
