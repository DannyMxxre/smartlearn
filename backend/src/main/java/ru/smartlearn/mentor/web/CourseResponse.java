package ru.smartlearn.mentor.web;

/**
 * Курс в ответе API.
 *
 * @param questionId задание-пробел
 * @param gapTitle тема пробела
 * @param title название курса
 * @param provider площадка
 * @param free бесплатный ли курс
 * @param url ссылка
 * @param week неделя плана
 */
public record CourseResponse(
		String questionId,
		String gapTitle,
		String title,
		String provider,
		boolean free,
		String url,
		int week
) {
}
