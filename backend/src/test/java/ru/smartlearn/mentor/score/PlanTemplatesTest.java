package ru.smartlearn.mentor.score;

import org.junit.jupiter.api.Test;
import ru.smartlearn.mentor.dialog.Role;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет, что план зависит от баллов, а не от роли целиком.
 */
class PlanTemplatesTest {

	@Test
	void weakTopicComesBeforeAStrongOne() {
		List<PlanWeek> plan = PlanTemplates.fromScores(Role.DEVELOPER, List.of(
				new AnswerScore("d-prompts", "Промптинг под задачи разработки", 3, "Промпт уже с форматом diff."),
				new AnswerScore("d-verify", "Верификация сгенерированного кода", 0, "Ответ не показывает рабочий приём."),
				new AnswerScore("d-workflow", "ИИ в ежедневной разработке", 3, "Шаг уже встроен."),
				new AnswerScore("d-limits", "Где ИИ использовать опасно", 2, "Секреты не отправляет."),
				new AnswerScore("d-iterate", "Уточнение контекста и итерации", 3, "Контекст уточняет.")
		));
		assertEquals("Верификация сгенерированного кода", plan.getFirst().title());
		assertTrue(plan.getFirst().steps().getFirst().contains("Ответ не показывает рабочий приём."));
		assertEquals("Закрепить: Где ИИ использовать опасно", plan.get(1).title());
		assertTrue(plan.get(1).steps().getFirst().contains("Секреты не отправляет."));
	}

	@Test
	void hidesTheInternalFallbackSentence() {
		List<PlanWeek> plan = PlanTemplates.fromScores(Role.DEVELOPER, List.of(
				new AnswerScore("d-workflow", "ИИ в ежедневной разработке", 2,
						"Модель не вернула балл этой темы. Локально: Есть конкретные действия и способ проверить результат.")
		));
		assertTrue(plan.getFirst().steps().getFirst().contains("Есть конкретные действия"));
		assertTrue(plan.getFirst().steps().stream().noneMatch(step -> step.contains("Модель не вернула")));
	}

	@Test
	void fifthGapIsNamedOnTheLastWeek() {
		List<PlanWeek> plan = PlanTemplates.fromScores(Role.ANALYST, List.of(
				new AnswerScore("a-prompts", "Формулирование задач и промптов", 0, ""),
				new AnswerScore("a-validation", "Критическая проверка результата", 0, ""),
				new AnswerScore("a-workflow", "Применение ИИ в рабочем процессе", 0, ""),
				new AnswerScore("a-limits", "Ограничения и границы применимости", 0, ""),
				new AnswerScore("a-iterate", "Итерации и доработка контекста", 0, "")
		));
		assertEquals(4, plan.size());
		assertTrue(plan.get(3).steps().stream().anyMatch(step -> step.contains("Итерации и доработка контекста")));
	}
}
