package ru.smartlearn.mentor.score;

import org.junit.jupiter.api.Test;
import ru.smartlearn.mentor.dialog.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Фиксирует границы уровня, от которых зависит честная оценка.
 */
class LevelScaleTest {

	/**
	 * Проверяет все четыре ступени шкалы на границах.
	 */
	@Test
	void mapsTotalsToLevels() {
		assertEquals(Level.NOVICE, LevelScale.fromTotal(0));
		assertEquals(Level.NOVICE, LevelScale.fromTotal(3));
		assertEquals(Level.BASIC, LevelScale.fromTotal(4));
		assertEquals(Level.BASIC, LevelScale.fromTotal(6));
		assertEquals(Level.CONFIDENT, LevelScale.fromTotal(7));
		assertEquals(Level.CONFIDENT, LevelScale.fromTotal(9));
		assertEquals(Level.ADVANCED, LevelScale.fromTotal(10));
		assertEquals(Level.ADVANCED, LevelScale.fromTotal(12));
	}
}
