package ru.smartlearn.mentor.score;

import ru.smartlearn.mentor.dialog.MentorSession;

/**
 * Считает отчёт по уже собранным ответам сессии.
 */
public interface Assessor {

	/**
	 * Оценивает ответы и собирает план.
	 *
	 * @param session сессия с ролью и ответами
	 * @return отчёт с уровнем по сумме баллов
	 */
	Assessment assess(MentorSession session);
}
