package ru.smartlearn.mentor.dialog;

/**
 * Роль сотрудника, под которую подбирается диагностика.
 */
public enum Role {

	/** Системный или бизнес-аналитик. */
	ANALYST("аналитик"),

	/** Backend или full-stack разработчик. */
	DEVELOPER("разработчик");

	private final String title;

	/**
	 * Сохраняет человекочитаемое название роли.
	 *
	 * @param title название на русском
	 */
	Role(String title) {
		this.title = title;
	}

	/**
	 * Возвращает название роли для текстов наставника.
	 *
	 * @return «аналитик» или «разработчик»
	 */
	public String title() {
		return title;
	}
}
