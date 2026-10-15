package ru.smartlearn.mentor.web;

import java.util.List;

/**
 * Неделя плана в ответе API.
 *
 * @param week номер недели
 * @param title тема недели
 * @param steps действия
 */
public record PlanWeekResponse(int week, String title, List<String> steps) {
}
