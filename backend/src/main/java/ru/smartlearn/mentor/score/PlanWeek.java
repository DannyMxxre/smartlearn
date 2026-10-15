package ru.smartlearn.mentor.score;

import java.util.List;

/**
 * Одна неделя плана развития.
 *
 * @param week номер недели, с 1
 * @param title тема недели
 * @param steps конкретные действия
 */
public record PlanWeek(int week, String title, List<String> steps) {
}
