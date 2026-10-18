package ru.smartlearn.mentor.score;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Каталог настоящих курсов под темы диагностики.
 * Ссылки заданы заранее, модель их не придумывает.
 */
public final class CourseCatalog {

	private static final Map<String, List<Course>> BY_QUESTION = coursesByQuestion();

	private CourseCatalog() {
	}

	/**
	 * Подбирает курсы к ответам с баллом 0 или 1.
	 * Одинаковые ссылки не повторяются.
	 *
	 * @param scores оценки диагностики
	 * @return курсы в порядке недель
	 */
	public static List<Course> recommend(List<AnswerScore> scores) {
		List<Course> picked = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (AnswerScore score : scores) {
			if (score.score() > 1) {
				continue;
			}
			for (Course course : BY_QUESTION.getOrDefault(score.questionId(), List.of())) {
				if (seen.add(course.url())) {
					picked.add(new Course(
							score.questionId(),
							score.title(),
							course.title(),
							course.provider(),
							course.free(),
							course.url(),
							course.week()
					));
				}
			}
		}
		return List.copyOf(picked);
	}

	/**
	 * Собирает по два курса на тему: бесплатный и платный.
	 *
	 * @return каталог по коду задания
	 */
	private static Map<String, List<Course>> coursesByQuestion() {
		Course promptFree = course("a-task", "Промт-инжиниринг для LLM: техники и лучшие практики", "Stepik", true, "https://stepik.org/course/238462/promo", 1);
		Course promptPaid = course("a-task", "Промпт-инжиниринг для ChatGPT: практический курс", "Stepik", false, "https://stepik.org/course/238475/promo", 1);
		Course aiEveryone = course("a-check", "AI For Everyone", "Coursera", false, "https://www.coursera.org/learn/ai-for-everyone", 2);
		Course devPrompt = course("d-bug", "ChatGPT Prompt Engineering for Developers", "DeepLearning.AI", true, "https://www.deeplearning.ai/courses/chatgpt-prompt-eng", 2);
		Course privacyFree = course("a-limits", "Защита персональных данных — базовый курс", "Stepik", true, "https://stepik.org/course/178491/promo", 4);
		Course privacyPaid = course("d-secrets", "Google Cybersecurity", "Coursera", false, "https://www.coursera.org/professional-certificates/google-cybersecurity", 4);

		Map<String, List<Course>> catalog = new LinkedHashMap<>();
		catalog.put("a-prompts", List.of(promptFree, promptPaid));
		catalog.put("a-validation", List.of(shifted(promptFree, "a-validation", 2), shifted(aiEveryone, "a-validation", 2)));
		catalog.put("a-workflow", List.of(shifted(promptFree, "a-workflow", 3), shifted(promptPaid, "a-workflow", 3)));
		catalog.put("a-limits", List.of(privacyFree, shifted(privacyPaid, "a-limits", 4)));
		catalog.put("a-iterate", List.of(shifted(promptFree, "a-iterate", 2), shifted(aiEveryone, "a-iterate", 2)));
		catalog.put("d-prompts", List.of(shifted(devPrompt, "d-prompts", 1), shifted(promptPaid, "d-prompts", 1)));
		catalog.put("d-verify", List.of(shifted(devPrompt, "d-verify", 2), shifted(promptPaid, "d-verify", 2)));
		catalog.put("d-workflow", List.of(shifted(devPrompt, "d-workflow", 3), shifted(promptPaid, "d-workflow", 3)));
		catalog.put("d-limits", List.of(shifted(privacyFree, "d-limits", 4), shifted(privacyPaid, "d-limits", 4)));
		catalog.put("d-iterate", List.of(shifted(devPrompt, "d-iterate", 2), shifted(promptPaid, "d-iterate", 2)));
		return Map.copyOf(catalog);
	}

	/**
	 * Создаёт запись каталога.
	 *
	 * @param questionId код задания
	 * @param title название курса
	 * @param provider площадка
	 * @param free бесплатный ли курс
	 * @param url ссылка
	 * @param week неделя плана
	 * @return курс
	 */
	private static Course course(String questionId, String title, String provider, boolean free, String url, int week) {
		return new Course(questionId, "", title, provider, free, url, week);
	}

	/**
	 * Копирует курс на другое задание и другую неделю.
	 *
	 * @param source исходный курс
	 * @param questionId новое задание
	 * @param week новая неделя
	 * @return копия
	 */
	private static Course shifted(Course source, String questionId, int week) {
		return new Course(questionId, "", source.title(), source.provider(), source.free(), source.url(), week);
	}
}
