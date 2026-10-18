package ru.smartlearn.mentor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Разрешает запросы к API со страницы на Timeweb и с локальной разработки.
 * Чужой Origin Spring отклоняет ответом 403 «Invalid CORS request».
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	/**
	 * Открывает {@code /api/**} для домена приложения и локальных страниц.
	 *
	 * @param registry реестр CORS-правил
	 */
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins(
						"https://dannymxxre-smartlearn-a253.twc1.net",
						"http://localhost:8080",
						"http://127.0.0.1:8080",
						"http://localhost:5173"
				)
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*")
				.maxAge(3600);
	}
}
