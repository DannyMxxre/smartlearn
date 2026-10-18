package ru.smartlearn.mentor.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Подхватывает локальный файл {@code .env} до старта Spring.
 * Уже заданные переменные окружения не перетирает.
 */
public final class EnvFile {

	private EnvFile() {
	}

	/**
	 * Кладёт пары из {@code .env} в системные свойства.
	 * Ищет файл рядом с запуском и на уровень выше, чтобы его находил и Maven из каталога backend.
	 */
	public static void load() {
		Path file = find();
		if (file == null) {
			return;
		}
		try {
			apply(Files.readAllLines(file));
		} catch (IOException exception) {
			throw new IllegalStateException("Не удалось прочитать " + file, exception);
		}
	}

	/**
	 * Ищет первый существующий {@code .env}.
	 *
	 * @return путь или {@code null}
	 */
	private static Path find() {
		Path current = Path.of("").toAbsolutePath();
		Path here = current.resolve(".env");
		if (Files.isRegularFile(here)) {
			return here;
		}
		Path parent = current.getParent();
		if (parent == null) {
			return null;
		}
		Path above = parent.resolve(".env");
		if (Files.isRegularFile(above)) {
			return above;
		}
		return null;
	}

	/**
	 * Разбирает строки {@code КЛЮЧ=значение} и пропускает комментарии.
	 *
	 * @param lines строки файла
	 */
	private static void apply(List<String> lines) {
		for (String line : lines) {
			String text = line.trim();
			if (text.isEmpty() || text.startsWith("#")) {
				continue;
			}
			if (text.startsWith("export ")) {
				text = text.substring("export ".length()).trim();
			}
			int split = text.indexOf('=');
			if (split <= 0) {
				continue;
			}
			String key = text.substring(0, split).trim();
			String value = unquote(text.substring(split + 1).trim());
			if (key.isEmpty() || value.isEmpty()) {
				continue;
			}
			if (System.getenv(key) == null && System.getProperty(key) == null) {
				System.setProperty(key, value);
			}
		}
	}

	/**
	 * Снимает парные кавычки вокруг значения.
	 *
	 * @param value сырое значение
	 * @return значение без внешней пары кавычек
	 */
	private static String unquote(String value) {
		if (value.length() >= 2) {
			char first = value.charAt(0);
			char last = value.charAt(value.length() - 1);
			if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
				return value.substring(1, value.length() - 1);
			}
		}
		return value;
	}
}
