package ru.smartlearn.mentor.llm;

/**
 * Один запрос к языковой модели и её текстовый ответ.
 */
public interface LlmClient {

	/**
	 * Отправляет системную инструкцию и задание пользователя.
	 *
	 * @param systemPrompt правила оценки
	 * @param userPrompt ответы диагностики
	 * @return сырой текст модели
	 */
	String complete(String systemPrompt, String userPrompt);
}
