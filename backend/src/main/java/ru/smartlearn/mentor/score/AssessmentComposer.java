package ru.smartlearn.mentor.score;

import ru.smartlearn.mentor.dialog.Level;
import ru.smartlearn.mentor.dialog.Role;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Собирает отчёт так, чтобы уровень всегда следовал из баллов.
 * Пустые списки модели дополняет, но баллы не переписывает.
 */
public final class AssessmentComposer {

	/**
	 * Собирает отчёт и заново назначает уровень по сумме.
	 *
	 * @param role роль сотрудника
	 * @param scores оценки заданий в порядке диагностики
	 * @param strengths сильные стороны, можно пустые
	 * @param gaps пробелы, можно пустые
	 * @param summary короткий вывод, можно пустой
	 * @param plan план, заменяется шаблоном, если он не на 4 недели
	 * @param scoredBy источник текста отчёта
	 * @return готовый отчёт
	 */
	public Assessment compose(
			Role role,
			List<AnswerScore> scores,
			List<String> strengths,
			List<String> gaps,
			String summary,
			List<PlanWeek> plan,
			String scoredBy
	) {
		int total = scores.stream().mapToInt(AnswerScore::score).sum();
		int maxScore = scores.size() * 3;
		Level level = LevelScale.fromShare(total, maxScore);
		List<PlanWeek> basePlan = valid(plan) ? copyPlan(plan) : PlanTemplates.forRole(role);
		List<Course> courses = CourseCatalog.recommend(scores);
		return new Assessment(
				level,
				total,
				maxScore,
				List.copyOf(scores),
				fillStrengths(scores, strengths),
				fillGaps(scores, gaps),
				summary == null || summary.isBlank() ? defaultSummary(level) : summary.trim(),
				attachCourses(basePlan, courses),
				scoredBy,
				courses
		);
	}

	/**
	 * Дописывает в неделю плана шаг с подобранным курсом.
	 *
	 * @param plan уже проверенный план
	 * @param courses курсы слабых тем
	 * @return план с прикреплёнными курсами
	 */
	private List<PlanWeek> attachCourses(List<PlanWeek> plan, List<Course> courses) {
		Map<Integer, List<String>> extra = new LinkedHashMap<>();
		for (Course course : courses) {
			extra.computeIfAbsent(course.week(), ignored -> new ArrayList<>())
					.add("Пройти курс: " + course.title() + " (" + course.provider() + ").");
		}
		List<PlanWeek> attached = new ArrayList<>();
		for (PlanWeek week : plan) {
			List<String> steps = new ArrayList<>(week.steps());
			steps.addAll(extra.getOrDefault(week.week(), List.of()));
			attached.add(new PlanWeek(week.week(), week.title(), List.copyOf(steps)));
		}
		return List.copyOf(attached);
	}

	/**
	 * Оставляет сильные стороны модели или собирает их из баллов 2 и 3.
	 *
	 * @param scores оценки заданий
	 * @param strengths присланный список
	 * @return непустой список
	 */
	private List<String> fillStrengths(List<AnswerScore> scores, List<String> strengths) {
		List<String> derived = new ArrayList<>();
		for (AnswerScore score : scores) {
			if (score.score() >= 2) {
				derived.add("Уверенно: " + score.title());
			}
		}
		if (derived.isEmpty()) {
			derived.add("Пока нет устойчивого приёма, на который можно опереться.");
		}
		return List.copyOf(derived);
	}

	/**
	 * Оставляет пробелы модели или собирает их из баллов 0 и 1.
	 *
	 * @param scores оценки заданий
	 * @param gaps присланный список
	 * @return непустой список
	 */
	private List<String> fillGaps(List<AnswerScore> scores, List<String> gaps) {
		List<String> derived = new ArrayList<>();
		for (AnswerScore score : scores) {
			if (score.score() <= 1) {
				derived.add("Подтянуть: " + score.title());
			}
		}
		if (derived.isEmpty()) {
			derived.add("Явного провала нет. Имеет смысл закрепить приёмы на рабочем кейсе.");
		}
		return List.copyOf(derived);
	}

	/**
	 * Проверяет, что план состоит из четырёх недель с шагами.
	 *
	 * @param plan план модели или шаблон
	 * @return {@code true}, если план можно показывать
	 */
	private boolean valid(List<PlanWeek> plan) {
		if (plan == null || plan.size() != 4) {
			return false;
		}
		for (PlanWeek week : plan) {
			if (week.title() == null || week.title().isBlank() || week.steps() == null || week.steps().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Копирует план, чтобы снаружи его нельзя было изменить.
	 *
	 * @param plan исходный план
	 * @return неизменяемая копия
	 */
	private List<PlanWeek> copyPlan(List<PlanWeek> plan) {
		List<PlanWeek> copy = new ArrayList<>();
		for (PlanWeek week : plan) {
			copy.add(new PlanWeek(week.week(), week.title().trim(), List.copyOf(week.steps())));
		}
		return List.copyOf(copy);
	}

	/**
	 * Даёт короткий вывод, если модель или эвристика его не прислали.
	 *
	 * @param level уже посчитанный уровень
	 * @return одно предложение
	 */
	private String defaultSummary(Level level) {
		return switch (level) {
			case NOVICE -> "ИИ пока используется эпизодически, без устойчивой проверки результата.";
			case BASIC -> "Базовое применение есть, не хватает проверки качества и ясных ограничений.";
			case CONFIDENT -> "Рабочий приём и проверка уже есть, часть тем ещё сырая.";
			case ADVANCED -> "Ответы показывают устойчивый приём: постановка задачи, проверка и границы.";
		};
	}
}
