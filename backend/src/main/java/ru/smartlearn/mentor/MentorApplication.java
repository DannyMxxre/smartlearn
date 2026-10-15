package ru.smartlearn.mentor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.smartlearn.mentor.config.YandexProperties;

/**
 * Точка входа бэкенда наставника.
 * Поднимает веб-приложение и привязывает настройки YandexGPT.
 */
@SpringBootApplication
@EnableConfigurationProperties(YandexProperties.class)
public class MentorApplication {

	/**
	 * Запускает Spring Boot.
	 *
	 * @param args аргументы командной строки
	 */
	public static void main(String[] args) {
		SpringApplication.run(MentorApplication.class, args);
	}

}
