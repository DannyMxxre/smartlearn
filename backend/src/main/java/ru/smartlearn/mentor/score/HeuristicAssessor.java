package ru.smartlearn.mentor.score;

import org.springframework.stereotype.Component;
import ru.smartlearn.mentor.dialog.MentorSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Оценивает ответы без облака, по конкретике текста.
 * Нужен, чтобы сценарий и интерфейс работали без ключа YandexGPT.
 */
@Component
public class HeuristicAssessor implements Assessor {

	private static final Pattern SHRUG = Pattern.compile("не знаю|не пользу|хз|понятия не имею|без разницы|затрудняюсь|никак");
	private static final String[] SIGNALS = {
			"промпт", "провер", "критер", "галлюцин", "секрет", "персональн",
			"ревью", "тест", "источник", "противореч", "валид", "дифф", "регресс",
			"огранич", "обезлич", "не отправ"
	};

	private final AssessmentComposer composer = new AssessmentComposer();

	/**
	 * Ставит 0–3 балла каждому ответу и собирает отчёт.
	 *
	 * @param session завершённая диагностика
	 * @return отчёт с пометкой {@code heuristic}
	 */
	@Override
	public Assessment assess(MentorSession session) {
		List<AnswerScore> scores = new ArrayList<>();
		for (StoredAnswer answer : session.answers()) {
			int score = scoreAnswer(answer.answer());
			scores.add(new AnswerScore(answer.questionId(), answer.title(), score, reason(score)));
		}
		return composer.compose(
				session.role(),
				scores,
				List.of(),
				List.of(),
				"",
				PlanTemplates.forRole(session.role()),
				"heuristic"
		);
	}

	/**
	 * Ставит балл по длине и по признакам рабочего приёма.
	 *
	 * @param answer текст ответа
	 * @return балл от 0 до 3
	 */
	public int scoreAnswer(String answer) {
		String text = answer == null ? "" : answer.toLowerCase(Locale.ROOT).trim();
		int signals = countSignals(text);
		if (text.length() < 40 || (signals == 0 && SHRUG.matcher(text).find() && text.length() < 160)) {
			return 0;
		}
		if (signals >= 3 && text.length() >= 220) {
			return 3;
		}
		if (signals >= 2 && text.length() >= 120) {
			return 2;
		}
		if (signals >= 1 || text.length() >= 80) {
			return 1;
		}
		return 0;
	}

	/**
	 * Считает, сколько признаков проверки и ограничений есть в ответе.
	 *
	 * @param text ответ в нижнем регистре
	 * @return число найденных признаков
	 */
	private int countSignals(String text) {
		int count = 0;
		for (String signal : SIGNALS) {
			if (text.contains(signal)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Объясняет балл короткой фразой для отчёта.
	 *
	 * @param score балл ответа
	 * @return обоснование
	 */
	private String reason(int score) {
		return switch (score) {
			case 3 -> "Есть конкретный приём, проверка результата и ограничение.";
			case 2 -> "Есть конкретные действия и способ проверить результат.";
			case 1 -> "Идея есть, но без проверки качества.";
			default -> "Ответ не показывает рабочий приём.";
		};
	}
}
