package ru.smartlearn.mentor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки OpenAI-совместимого вызова YandexGPT.
 * Секреты приходят из окружения и не хранятся в репозитории.
 */
@ConfigurationProperties(prefix = "yandex")
public record YandexProperties(
		String baseUrl,
		String apiKey,
		String folderId,
		String model,
		String authScheme,
		double temperature,
		int maxTokens
) {

	/**
	 * Подставляет безопасные значения, если часть настроек не задана.
	 */
	public YandexProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			baseUrl = "https://ai.api.cloud.yandex.net/v1";
		}
		if (apiKey == null) {
			apiKey = "";
		}
		if (folderId == null) {
			folderId = "";
		}
		if (model == null) {
			model = "";
		}
		if (authScheme == null || authScheme.isBlank()) {
			authScheme = "Api-Key";
		}
	}

	/**
	 * Проверяет, хватает ли данных, чтобы звать облако.
	 *
	 * @return {@code true}, если заданы и ключ, и каталог
	 */
	public boolean configured() {
		return !apiKey.isBlank() && !folderId.isBlank();
	}

	/**
	 * Возвращает идентификатор модели.
	 * Пустая настройка превращается в URI YandexGPT в каталоге пользователя.
	 *
	 * @return модель для поля {@code model}
	 */
	public String resolvedModel() {
		if (!model.isBlank()) {
			return model;
		}
		return "gpt://" + folderId + "/yandexgpt/latest";
	}
}
