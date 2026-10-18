package ru.smartlearn.mentor.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.smartlearn.mentor.config.GigaChatProperties;
import ru.smartlearn.mentor.dialog.MentorSession;
import ru.smartlearn.mentor.score.Assessment;
import ru.smartlearn.mentor.score.Assessor;
import ru.smartlearn.mentor.score.HeuristicAssessor;

/**
 * Выбирает, кто пишет отчёт: GigaChat или локальный разборщик.
 * При любой ошибке облака диагностика всё равно заканчивается отчётом.
 */
@Service
public class AssessmentService implements Assessor {

	private static final Logger log = LoggerFactory.getLogger(AssessmentService.class);

	private final GigaChatProperties properties;
	private final LlmAssessor llmAssessor;
	private final HeuristicAssessor heuristicAssessor;

	/**
	 * Связывает облачный и локальный способы оценки.
	 *
	 * @param properties креды GigaChat
	 * @param llmAssessor разбор JSON модели
	 * @param heuristicAssessor запасной разборщик
	 */
	public AssessmentService(GigaChatProperties properties, LlmAssessor llmAssessor, HeuristicAssessor heuristicAssessor) {
		this.properties = properties;
		this.llmAssessor = llmAssessor;
		this.heuristicAssessor = heuristicAssessor;
	}

	/**
	 * Оценивает сессию. Без кредов или при сбое модели считает локально.
	 *
	 * @param session сессия с ответами
	 * @return отчёт
	 */
	@Override
	public Assessment assess(MentorSession session) {
		if (properties.configured()) {
			try {
				return llmAssessor.assess(session);
			} catch (RuntimeException exception) {
				log.warn("GigaChat не оценил сессию, считаю локально: {}", exception.getMessage());
			}
		}
		return heuristicAssessor.assess(session);
	}
}
