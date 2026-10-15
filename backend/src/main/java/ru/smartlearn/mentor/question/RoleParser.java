package ru.smartlearn.mentor.question;

import ru.smartlearn.mentor.dialog.Role;

import java.util.Locale;
import java.util.Optional;

/**
 * Достаёт роль из свободной фразы человека.
 * Сценарий не продолжается, пока роль не одна и не понятна.
 */
public final class RoleParser {

	private RoleParser() {
	}

	/**
	 * Ищет в тексте одну роль.
	 *
	 * @param text сообщение человека
	 * @return роль, если названа ровно одна
	 */
	public static Optional<Role> parse(String text) {
		String normalized = text.toLowerCase(Locale.ROOT);
		boolean analyst = containsAny(normalized, "аналит", "analyst", "системн", "бизнес-анал");
		boolean developer = containsAny(normalized, "разработ", "developer", "backend", "fullstack", "full-stack", "фулл", "бэкенд", "бекенд");
		if (analyst == developer) {
			return Optional.empty();
		}
		return Optional.of(analyst ? Role.ANALYST : Role.DEVELOPER);
	}

	/**
	 * Проверяет, встречается ли хотя бы одна подсказка.
	 *
	 * @param text уже приведённый к нижнему регистру текст
	 * @param needles фрагменты, по которым узнаём роль
	 * @return {@code true}, если фрагмент найден
	 */
	private static boolean containsAny(String text, String... needles) {
		for (String needle : needles) {
			if (text.contains(needle)) {
				return true;
			}
		}
		return false;
	}
}
