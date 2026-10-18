package ru.smartlearn.mentor.dialog;

/**
 * Шаг сценария, которым управляет сервер, а не модель.
 */
public enum Phase {

	/** Ждём выбор роли. */
	ASK_ROLE,

	/** Ждём цель на месяц. */
	ASK_GOAL,

	/** Ждём описание текущего опыта с ИИ. */
	ASK_SKILLS,

	/** Идёт серия заданий. */
	DIAGNOSIS,

	/** Отчёт уже выдан. */
	DONE;

	/**
	 * Сводит внутренний шаг к фазе, которую показывает интерфейс.
	 *
	 * @return {@code intro}, {@code diagnosis} или {@code report}
	 */
	public String wireName() {
		return switch (this) {
			case ASK_ROLE, ASK_GOAL, ASK_SKILLS -> "intro";
			case DIAGNOSIS -> "diagnosis";
			case DONE -> "report";
		};
	}
}
