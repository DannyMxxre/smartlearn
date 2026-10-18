package ru.smartlearn.mentor.llm;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.smartlearn.mentor.config.GigaChatProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Клиент GigaChat: сначала обмен ключа авторизации на токен, затем chat completion.
 * Токен живёт около 30 минут и переиспользуется.
 */
@Component
public class GigaChatClient implements LlmClient {

	private static final List<String> CERTIFICATES = List.of(
			"certs/russian_trusted_root_ca.crt",
			"certs/russian_trusted_sub_ca.crt"
	);

	private final GigaChatProperties properties;
	private final RestClient client;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private String accessToken = "";
	private long expiresAtMillis;

	/**
	 * Собирает HTTP-клиент, который доверяет и обычным CA, и сертификату Минцифры.
	 *
	 * @param properties креды и адреса GigaChat
	 */
	public GigaChatClient(GigaChatProperties properties) {
		this.properties = properties;
		HttpClient httpClient = HttpClient.newBuilder().sslContext(sslContext()).build();
		this.client = RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(httpClient)).build();
	}

	/**
	 * Получает токен при необходимости и возвращает текст ответа модели.
	 *
	 * @param systemPrompt правила оценки
	 * @param userPrompt реплика или ответы диагностики
	 * @return содержимое {@code choices[0].message.content}
	 */
	@Override
	public String complete(String systemPrompt, String userPrompt) {
		if (!properties.configured()) {
			throw new IllegalStateException("GigaChat не настроен: нужны GIGACHAT_CLIENT_ID, GIGACHAT_SCOPE и GIGACHAT_AUTHORIZATION_KEY");
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
					.uri(properties.baseUrl() + "/v1/chat/completions")
					.header("Authorization", "Bearer " + token())
					.header("Content-Type", "application/json")
					.body(jsonMapper.writeValueAsString(body))
					.retrieve()
					.body(String.class);
			String content = jsonMapper.readTree(raw).path("choices").path(0).path("message").path("content").asString("");
			if (content.isBlank()) {
				throw new IllegalStateException("GigaChat вернул пустой ответ");
			}
			return content;
		} catch (RestClientResponseException exception) {
			throw new IllegalStateException("GigaChat вернул " + exception.getStatusCode(), exception);
		}
	}

	/**
	 * Возвращает действующий токен доступа, обновляя его ближе к концу срока.
	 *
	 * @return access token
	 */
	private synchronized String token() {
		if (!accessToken.isBlank() && System.currentTimeMillis() + 60_000 < expiresAtMillis) {
			return accessToken;
		}
		try {
			String raw = client.post()
					.uri(properties.authUrl() + "/api/v2/oauth")
					.header("Authorization", properties.basicAuthorization())
					.header("RqUID", UUID.randomUUID().toString())
					.header("Content-Type", "application/x-www-form-urlencoded")
					.header("Accept", "application/json")
					.body("scope=" + URLEncoder.encode(properties.scope(), StandardCharsets.UTF_8))
					.retrieve()
					.body(String.class);
			JsonNode root = jsonMapper.readTree(raw);
			String token = root.path("access_token").asString("");
			if (token.isBlank()) {
				throw new IllegalStateException("В ответе GigaChat нет access_token");
			}
			accessToken = token;
			expiresAtMillis = expiresAt(root.path("expires_at").asLong(0));
			return accessToken;
		} catch (RestClientResponseException exception) {
			throw new IllegalStateException("GigaChat не выдал токен: " + exception.getStatusCode(), exception);
		}
	}

	/**
	 * Приводит {@code expires_at} к миллисекундам.
	 * В ответе API встречается и секунды, и миллисекунды.
	 *
	 * @param raw значение из JSON
	 * @return момент истечения в миллисекундах
	 */
	private long expiresAt(long raw) {
		if (raw <= 0) {
			return System.currentTimeMillis() + 25 * 60_000L;
		}
		if (raw < 1_000_000_000_000L) {
			return raw * 1000;
		}
		return raw;
	}

	/**
	 * Собирает SSL-контекст: системные CA плюс корневой и выпускающий сертификаты Минцифры.
	 *
	 * @return контекст для HTTPS-клиента
	 */
	private static SSLContext sslContext() {
		try {
			KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
			Path cacerts = Path.of(System.getProperty("java.home"), "lib", "security", "cacerts");
			try (InputStream input = Files.newInputStream(cacerts)) {
				store.load(input, "changeit".toCharArray());
			}
			CertificateFactory factory = CertificateFactory.getInstance("X.509");
			int index = 0;
			for (String name : CERTIFICATES) {
				try (InputStream input = GigaChatClient.class.getClassLoader().getResourceAsStream(name)) {
					if (input == null) {
						throw new IllegalStateException("Нет сертификата " + name);
					}
					store.setCertificateEntry("russian-trusted-" + index++, factory.generateCertificate(input));
				}
			}
			TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			trust.init(store);
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, trust.getTrustManagers(), null);
			return context;
		} catch (GeneralSecurityException | IOException exception) {
			throw new IllegalStateException("Не удалось собрать доверие к сертификату Минцифры", exception);
		}
	}
}
