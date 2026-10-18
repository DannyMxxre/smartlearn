package ru.smartlearn.mentor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки GigaChat: кабинетные креды и адреса API.
 * Секреты приходят из {@code .env} и не хранятся в репозитории.
 */
@ConfigurationProperties(prefix = "gigachat")
public record GigaChatProperties(
		String clientId,
		String scope,
		String authorizationKey,
		String authUrl,
		String baseUrl,
		String model,
		double temperature,
		int maxTokens
) {

	/**
	 * Подставляет адреса и модель, если в файле указаны только креды.
	 */
	public GigaChatProperties {
		if (clientId == null) {
			clientId = "";
		}
		if (scope == null) {
			scope = "";
		}
		if (authorizationKey == null) {
			authorizationKey = "";
		}
		if (authUrl == null || authUrl.isBlank()) {
			authUrl = "https://ngw.devices.sberbank.ru:9443";
		}
		if (baseUrl == null || baseUrl.isBlank()) {
			baseUrl = "https://api.giga.chat";
		}
		if (model == null) {
			model = "";
		}
	}

	/**
	 * Проверяет, хватает ли кабинетных данных, чтобы звать GigaChat.
	 *
	 * @return {@code true}, если заданы Client ID, Scope и ключ авторизации
	 */
	public boolean configured() {
		return !clientId.isBlank() && !scope.isBlank() && !authorizationKey.isBlank();
	}

	/**
	 * Возвращает имя модели.
	 * Пустая настройка означает обычный GigaChat второго поколения.
	 *
	 * @return поле {@code model} для chat/completions
	 */
	public String resolvedModel() {
		if (!model.isBlank()) {
			return model;
		}
		return "GigaChat-2";
	}

	/**
	 * Собирает заголовок Basic из ключа авторизации.
	 * Если в файле ключ уже начинается с {@code Basic}, префикс не дублируется.
	 *
	 * @return значение заголовка {@code Authorization} для обмена токена
	 */
	public String basicAuthorization() {
		String key = authorizationKey.trim();
		if (key.regionMatches(true, 0, "Basic ", 0, "Basic ".length())) {
			return key;
		}
		return "Basic " + key;
	}
}
