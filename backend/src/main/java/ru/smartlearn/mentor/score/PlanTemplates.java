package ru.smartlearn.mentor.score;

import ru.smartlearn.mentor.dialog.Role;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Собирает план на месяц из баллов компетенций.
 * Одинаковый профиль даёт одинаковые недели, другой набор баллов — другие.
 */
public final class PlanTemplates {

	private static final Map<String, String> ACTIONS = Map.ofEntries(
			Map.entry("a-prompts", "Собери промпт из роли, цели и формата user story, затем сверь результат с исходной заметкой."),
			Map.entry("a-validation", "Сравни ответ модели с источником и выпиши одну фразу, которой в источнике не было."),
			Map.entry("a-workflow", "Выбери один шаг работы и запиши, что подаёшь модели на вход и что проверяешь на выходе."),
			Map.entry("a-limits", "Отметь, какие данные из задачи нельзя отправлять в модель, и замени их учебным примером."),
			Map.entry("a-iterate", "Возьми слабый ответ модели, добавь один кусок контекста и сравни второй результат с первым."),
			Map.entry("d-prompts", "Напиши промпт со стеком, ограничением и форматом diff, затем проверь ответ до коммита."),
			Map.entry("d-verify", "Прогони тест и просмотри diff, прежде чем принять патч модели."),
			Map.entry("d-workflow", "Встрой модель в один ежедневный шаг: зафиксируй вход и свою проверку на выходе."),
			Map.entry("d-limits", "Убери из примера секреты и прод-данные и задай вопрос модели уже на обезличенном тексте."),
			Map.entry("d-iterate", "Уточни контекст после неудачного diff и проверь вторую версию тестом.")
	);

	private PlanTemplates() {
	}

	/**
	 * Возвращает четыре недели под роль, если баллов ещё нет.
	 *
	 * @param role роль сотрудника
	 * @return план по умолчанию
	 */
	public static List<PlanWeek> forRole(Role role) {
		if (role == Role.ANALYST) {
			return List.of(
					week(1, "Шаблон промпта для требований",
							"Возьми одну сырую заметку и собери промпт: роль, контекст, формат user story и критерии приёмки.",
							"Сохрани удачный промпт и переиспользуй его на следующей задаче."),
					week(2, "Сверка с источником",
							"После каждого ответа модели отмечай, какая фраза подтверждена исходным текстом, а какая додумана.",
							"Добавь в промпт просьбу отдельно перечислять противоречия и открытые вопросы."),
					week(3, "Рабочий кейс",
							"Разбери одну постановку до акторов, целей и открытых вопросов.",
							"Отдай результат человеку на ревью и поправь промпт по его замечаниям."),
					week(4, "Данные и ответственность",
							"Выпиши, какие данные из требований нельзя отправлять в модель.",
							"Закрепи правило: требование в бэклог попадает только после твоей проверки.")
			);
		}
		return List.of(
				week(1, "Промпт под задачу в коде",
						"Сформулируй шаблон: версия стека, ограничение, нужный формат diff и чего делать нельзя.",
						"Прогони шаблон на одной небольшой задаче и сохрани его."),
				week(2, "Проверка diff и тестов",
						"Перед принятием ответа модели проверяй: код компилируется, тест запускается, diff не шире задачи.",
						"Составь короткий чеклист ревью и держи его рядом с редактором."),
				week(3, "Рабочий кейс",
						"Сделай одну REST-ручку или исправление теста вместе с моделью и запиши, где она ошиблась.",
						"Поправь промпт так, чтобы повторить ошибку было труднее."),
				week(4, "Секреты и границы",
						"Отдели учебный пример от конфигов, токенов и прод-данных.",
						"Проверь, что в контекст агента не попадает файл с секретами.")
		);
	}

	/**
	 * Строит четыре недели по баллам: сначала слабые темы, затем закрепление сильных.
	 *
	 * @param role роль, если баллов ещё нет
	 * @param scores оценки компетенций в порядке карты
	 * @return план на месяц
	 */
	public static List<PlanWeek> fromScores(Role role, List<AnswerScore> scores) {
		if (scores == null || scores.isEmpty()) {
			return forRole(role);
		}
		List<AnswerScore> gaps = new ArrayList<>();
		List<AnswerScore> solid = new ArrayList<>();
		for (AnswerScore score : scores) {
			if (score.score() <= 1) {
				gaps.add(score);
			} else {
				solid.add(score);
			}
		}
		gaps.sort(Comparator.comparingInt(AnswerScore::score));
		solid.sort(Comparator.comparingInt(AnswerScore::score));
		List<AnswerScore> picked = new ArrayList<>();
		picked.addAll(gaps);
		picked.addAll(solid);
		List<PlanWeek> weeks = new ArrayList<>();
		int shown = Math.min(4, picked.size());
		for (int index = 0; index < shown; index++) {
			weeks.add(weekFor(index + 1, picked.get(index)));
		}
		if (picked.size() > 4) {
			AnswerScore extra = picked.get(4);
			PlanWeek last = weeks.get(3);
			List<String> steps = new ArrayList<>(last.steps());
			steps.add("Следом закрой тему «" + extra.title() + "». " + action(extra));
			weeks.set(3, new PlanWeek(4, last.title(), List.copyOf(steps)));
		}
		return List.copyOf(weeks);
	}

	/**
	 * Собирает неделю из балла и короткого разбора ответа.
	 *
	 * @param number номер недели
	 * @param score оценка темы
	 * @return неделя плана
	 */
	private static PlanWeek weekFor(int number, AnswerScore score) {
		String practice = action(score);
		String because = clip(score.reason());
		if (score.score() <= 1) {
			String first = because.isBlank()
					? practice
					: "По твоим ответам: " + because;
			return new PlanWeek(number, score.title(), List.of(first, practice));
		}
		String first = because.isBlank()
				? "Приём по этой теме уже есть, его не нужно учить заново."
				: "Это уже получается: " + because;
		return new PlanWeek(number, "Закрепить: " + score.title(), List.of(
				first,
				"Перенеси этот приём на следующую свою задачу и проверь результат тем же способом."
		));
	}

	/**
	 * Возвращает одно упражнение темы.
	 *
	 * @param score оценка темы
	 * @return текст шага
	 */
	private static String action(AnswerScore score) {
		return ACTIONS.getOrDefault(score.questionId(), "Повтори тему «" + score.title() + "» на новом коротком примере.");
	}

	/**
	 * Обрезает обоснование балла, чтобы неделя оставалась короткой.
	 *
	 * @param reason текст разбора
	 * @return одна фраза или пустая строка
	 */
	private static String clip(String reason) {
		if (reason == null || reason.isBlank()) {
			return "";
		}
		String text = reason.trim().replaceAll("\\s+", " ");
		int local = text.indexOf("Локально: ");
		if (text.contains("Модель не вернула") && local >= 0) {
			text = text.substring(local + "Локально: ".length()).trim();
		} else if (text.contains("Модель не вернула")) {
			return "";
		}
		if (text.length() <= 180) {
			return text;
		}
		int end = text.lastIndexOf(' ', 180);
		if (end < 80) {
			end = 180;
		}
		return text.substring(0, end).trim() + "…";
	}

	/**
	 * Собирает одну неделю из заголовка и двух шагов.
	 *
	 * @param number номер недели
	 * @param title тема недели
	 * @param first первый шаг
	 * @param second второй шаг
	 * @return неделя плана
	 */
	private static PlanWeek week(int number, String title, String first, String second) {
		return new PlanWeek(number, title, List.of(first, second));
	}
}
