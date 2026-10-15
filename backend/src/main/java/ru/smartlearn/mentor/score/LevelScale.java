package ru.smartlearn.mentor.score;

import ru.smartlearn.mentor.dialog.Level;

/**
 * Переводит сумму баллов в уровень.
 * Границы фиксированы, чтобы повторный прогон не плавал из-за формулировок.
 */
public final class LevelScale {

	private LevelScale() {
	}

	/**
	 * Назначает уровень по сумме четырёх оценок от 0 до 3.
	 *
	 * @param score сумма баллов
	 * @return уровень шкалы
	 */
	public static Level fromTotal(int score) {
		if (score <= 3) {
			return Level.NOVICE;
		}
		if (score <= 6) {
			return Level.BASIC;
		}
		if (score <= 9) {
			return Level.CONFIDENT;
		}
		return Level.ADVANCED;
	}
}
