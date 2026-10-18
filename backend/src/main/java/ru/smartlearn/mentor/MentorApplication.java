package ru.smartlearn.mentor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.smartlearn.mentor.config.EnvFile;
import ru.smartlearn.mentor.config.GigaChatProperties;

/**
 * Точка входа бэкенда наставника.
 * Поднимает веб-приложение и привязывает настройки GigaChat.
 */
@SpringBootApplication
@EnableConfigurationProperties(GigaChatProperties.class)
public class MentorApplication {

	/**
	 * Запускает Spring Boot.
	 *
	 * @param args аргументы командной строки
	 */
	public static void main(String[] args) {
		EnvFile.load();
		SpringApplication.run(MentorApplication.class, args);
	}

}
