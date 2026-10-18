package ru.smartlearn.mentor.score;

import java.util.ArrayList;
import java.util.List;

/**
 * Сводит несколько оценок одной компетенции к одному баллу 0–3.
 * Уровень по этой сумме потом считает код, не модель.
 */
public final class CompetencyRollup {

	private CompetencyRollup() {
	}

	/**
	 * Считает средний балл по каждой компетенции карты.
	 * Тема без единого ответа получает 0.
	 *
	 * @param competencies компетенции роли в фиксированном порядке
	 * @param observations все оценки реплик и бонусных заданий
	 * @return одна оценка на компетенцию
	 */
	public static List<AnswerScore> rollup(List<Competency> competencies, List<AnswerScore> observations) {
		List<AnswerScore> scores = new ArrayList<>();
		for (Competency competency : competencies) {
			int sum = 0;
			int count = 0;
			String reason = "Компетенция в диалоге не раскрыта.";
			for (AnswerScore observation : observations) {
				if (!competency.id().equals(observation.questionId())) {
					continue;
				}
				sum += observation.score();
				count++;
				if (observation.reason() != null && !observation.reason().isBlank()) {
					reason = observation.reason();
				}
			}
			int score = count == 0 ? 0 : (int) Math.round(sum / (double) count);
			scores.add(new AnswerScore(competency.id(), competency.title(), Math.max(0, Math.min(3, score)), reason));
		}
		return List.copyOf(scores);
	}
}
