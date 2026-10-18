package ru.smartlearn.mentor.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Отвечает на проверку живости платформы.
 * Корневая страница для этого не подходит: хост может звать именно {@code /health}.
 */
@RestController
public class HealthController {

	/**
	 * Возвращает короткий ответ, пока процесс принимает запросы.
	 *
	 * @return строка {@code ok}
	 */
	@GetMapping("/health")
	public String health() {
		return "ok";
	}
}
