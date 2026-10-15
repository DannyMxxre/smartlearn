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
				new AnswerScore("d-bug", "Разбор падения и ревью патча", 0, ""),
				new AnswerScore("d-api", "Работа с несуществующим API", 0, ""),
				new AnswerScore("d-practice", "Промпт на рабочую ручку", 0, ""),
				new AnswerScore("d-secrets", "Секреты и данные", 0, "")
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
				new AnswerScore("a-task", "Постановка задачи модели", 3, ""),
				new AnswerScore("a-check", "Проверка противоречий", 3, ""),
				new AnswerScore("a-practice", "Практический разбор заметки", 2, ""),
				new AnswerScore("a-limits", "Границы применения", 2, "")
		));
		assertTrue(courses.isEmpty());
	}
}
