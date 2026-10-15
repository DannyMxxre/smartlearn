package ru.smartlearn.mentor.score;

/**
 * Курс из каталога, подобранный под конкретный пробел.
 *
 * @param questionId задание, из-за которого курс попал в план
 * @param gapTitle тема пробела
 * @param title название курса
 * @param provider площадка
 * @param free {@code true}, если курс бесплатный
 * @param url рабочая ссылка на страницу курса
 * @param week неделя плана, к которой курс прикреплён
 */
public record Course(
		String questionId,
		String gapTitle,
		String title,
		String provider,
		boolean free,
		String url,
		int week
) {
}
