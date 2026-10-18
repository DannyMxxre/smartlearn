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
		return fromShare(score, 12);
	}

	/**
	 * Назначает уровень по доле набранных баллов.
	 * 0–25% новичок, 26–50% базовый, 51–75% уверенный, выше продвинутый.
	 *
	 * @param score сумма баллов
	 * @param max максимум суммы
	 * @return уровень шкалы
	 */
	public static Level fromShare(int score, int max) {
		int percent = max <= 0 ? 0 : score * 100 / max;
		if (percent <= 25) {
			return Level.NOVICE;
		}
		if (percent <= 50) {
			return Level.BASIC;
		}
		if (percent <= 75) {
			return Level.CONFIDENT;
		}
		return Level.ADVANCED;
	}
}
