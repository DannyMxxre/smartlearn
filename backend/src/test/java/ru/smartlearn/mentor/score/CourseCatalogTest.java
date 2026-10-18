package ru.smartlearn.mentor.score;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет, что курсы берутся из каталога и только под слабые ответы.
 */
class CourseCatalogTest {

	/**
	 * Нулевые баллы разработчика дают и бесплатные, и платные ссылки.
	 */
	@Test
	void weakDeveloperAnswersGetRealCourses() {
		List<Course> courses = CourseCatalog.recommend(List.of(
				new AnswerScore("d-prompts", "Промптинг под задачи разработки", 0, ""),
				new AnswerScore("d-verify", "Верификация сгенерированного кода", 0, ""),
				new AnswerScore("d-workflow", "ИИ в ежедневной разработке", 0, ""),
				new AnswerScore("d-limits", "Где ИИ использовать опасно", 0, ""),
				new AnswerScore("d-iterate", "Уточнение контекста и итерации", 0, "")
		));
		assertTrue(courses.stream().anyMatch(Course::free));
		assertTrue(courses.stream().anyMatch(course -> !course.free()));
		assertTrue(courses.stream().allMatch(course -> course.url().startsWith("https://")));
		assertEquals(4, courses.stream().map(Course::url).distinct().count());
	}

	/**
	 * Сильные ответы курсов не получают.
	 */
	@Test
	void strongAnswersGetNoCourses() {
		List<Course> courses = CourseCatalog.recommend(List.of(
				new AnswerScore("a-prompts", "Формулирование задач и промптов", 3, ""),
				new AnswerScore("a-validation", "Критическая проверка результата", 3, ""),
				new AnswerScore("a-workflow", "Применение ИИ в рабочем процессе", 2, ""),
				new AnswerScore("a-limits", "Ограничения и границы применимости", 2, ""),
				new AnswerScore("a-iterate", "Итерации и доработка контекста", 2, "")
		));
		assertTrue(courses.isEmpty());
	}
}
