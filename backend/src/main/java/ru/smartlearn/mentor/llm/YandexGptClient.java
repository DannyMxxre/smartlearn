package ru.smartlearn.mentor.llm;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.smartlearn.mentor.config.YandexProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Клиент YandexGPT через OpenAI-совместимый endpoint.
 * Вызывается один раз в конце диагностики.
 */
@Component
public class YandexGptClient implements LlmClient {

	private final YandexProperties properties;
	private final RestClient client;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	/**
	 * Собирает HTTP-клиент на базовый адрес из настроек.
	 * В Spring Boot 4 билдер сам в контекст не кладётся, поэтому создаём его здесь.
	 *
	 * @param properties ключ, каталог и модель
	 */
	public YandexGptClient(YandexProperties properties) {
		this.properties = properties;
		this.client = RestClient.builder().baseUrl(properties.baseUrl()).build();
	}

	/**
	 * Делает chat completion и возвращает текст первого ответа.
	 *
	 * @param systemPrompt правила оценки
	 * @param userPrompt ответы диагностики
	 * @return содержимое {@code choices[0].message.content}
	 */
	@Override
	public String complete(String systemPrompt, String userPrompt) {
		if (!properties.configured()) {
			throw new IllegalStateException("YandexGPT не настроен: нужны YANDEX_API_KEY и YANDEX_FOLDER_ID");
		}
		Map<String, Object> body = Map.of(
				"model", properties.resolvedModel(),
				"temperature", properties.temperature(),
				"max_tokens", properties.maxTokens(),
				"messages", List.of(
						Map.of("role", "system", "content", systemPrompt),
						Map.of("role", "user", "content", userPrompt)
				)
		);
		try {
			String raw = client.post()
					.uri("/chat/completions")
					.header("Authorization", properties.authScheme() + " " + properties.apiKey())
					.header("OpenAI-Project", properties.folderId())
					.header("Content-Type", "application/json")
					.body(jsonMapper.writeValueAsString(body))
					.retrieve()
					.body(String.class);
			JsonNode root = jsonMapper.readTree(raw);
			return root.path("choices").path(0).path("message").path("content").asString("");
		} catch (RestClientResponseException exception) {
			throw new IllegalStateException("YandexGPT вернул " + exception.getStatusCode(), exception);
		}
	}
}
