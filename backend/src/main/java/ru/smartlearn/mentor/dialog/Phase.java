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

	/** Открытый диалог по карте компетенций. */
	DIALOGUE,

	/** Два практических кейса после диалога. */
	BONUS,

	/** Человек отрабатывает один пробел. */
	PRACTICE,

	/** Отчёт уже выдан. */
	DONE;

	/**
	 * Сводит внутренний шаг к фазе, которую показывает интерфейс.
	 *
	 * @return имя фазы для интерфейса
	 */
	public String wireName() {
		return switch (this) {
			case ASK_ROLE, ASK_GOAL, ASK_SKILLS -> "intro";
			case DIALOGUE -> "dialogue";
			case BONUS -> "bonus";
			case PRACTICE -> "practice";
			case DONE -> "report";
		};
	}
}
