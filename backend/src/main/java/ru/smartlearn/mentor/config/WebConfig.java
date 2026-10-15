package ru.smartlearn.mentor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Открывает API локальному фронту на Vite.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	/**
	 * Разрешает запросы чата с локальной страницы разработки.
	 *
	 * @param registry реестр CORS-правил
	 */
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins("http://localhost:5173")
				.allowedMethods("GET", "POST", "OPTIONS");
	}
}
