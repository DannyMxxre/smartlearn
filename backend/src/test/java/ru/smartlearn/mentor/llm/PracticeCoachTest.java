package ru.smartlearn.mentor.llm;

import org.junit.jupiter.api.Test;
import ru.smartlearn.mentor.config.GigaChatProperties;
import ru.smartlearn.mentor.dialog.Role;
import ru.smartlearn.mentor.score.CompetencyMap;
import ru.smartlearn.mentor.score.HeuristicAssessor;
import ru.smartlearn.mentor.score.PracticeGrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет, что тренажёр не подменяет задание требованиями про инструменты.
 */
class PracticeCoachTest {

	@Test
	void dropsInventedToolsAndKeepsTheChecklist() {
		PracticeCoach coach = new PracticeCoach(
				new GigaChatProperties("id", "scope", "key", "", "", "", 0, 200),
				new HeuristicAssessor(),
				(system, user) -> """
						{"score":1,"whyNotMax":"Не хватает инструментов согласования и прозрачности для стейкхолдеров.",
						"techniques":"Добавь Confluence, Jira и Slack.","nextTask":"Дополните промпт указанием Jira."}
						""",
				new CompetencyMap()
		);
		PracticeGrade grade = coach.grade(Role.ANALYST, "a-prompts", "Промпты", coach.taskFor("a-prompts"), "Напишу user story.");
		String combined = grade.whyNotMax() + "\n" + grade.techniques() + "\n" + grade.nextTask();
		assertFalse(combined.toLowerCase().contains("jira"));
		assertFalse(combined.toLowerCase().contains("confluence"));
		assertFalse(combined.toLowerCase().contains("стейкхолдер"));
		assertTrue(grade.nextTask().contains("В ответе нужно"));
		assertTrue(grade.nextTask().contains("Партнёры"));
	}

	@Test
	void namingEveryVisiblePointClosesTheGap() {
		PracticeCoach coach = new PracticeCoach(
				new GigaChatProperties("id", "scope", "key", "", "", "", 0, 200),
				new HeuristicAssessor(),
				(system, user) -> """
						{"score":1,"whyNotMax":"Не назван пункт про ручной процесс и автоматизацию.",
						"techniques":"Добавить автоматизацию.","nextTask":"Дополните промпт."}
						""",
				new CompetencyMap()
		);
		String answer = "Роль аналитика, цель — user story. На выходе user story и критерии приёмки. Сверю текст с заметкой и уберу лишнее.";
		PracticeGrade grade = coach.grade(Role.ANALYST, "a-prompts", "Промпты", coach.taskFor("a-prompts"), answer);
		assertTrue(grade.mastered());
		assertEquals(3, grade.score());
	}
}
