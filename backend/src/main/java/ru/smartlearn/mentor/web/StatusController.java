package ru.smartlearn.mentor.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.smartlearn.mentor.config.GigaChatProperties;

/**
 * Сообщает интерфейсу, подключён ли GigaChat.
 */
@RestController
@RequestMapping("/api/status")
public class StatusController {

	private final GigaChatProperties properties;

	/**
	 * Берёт настройки GigaChat, чтобы проверить наличие кредов.
	 *
	 * @param properties Client ID, Scope и ключ авторизации
	 */
	public StatusController(GigaChatProperties properties) {
		this.properties = properties;
	}

	/**
	 * Возвращает, заданы ли все три кабинетных значения.
	 * Сам ключ в ответ не входит.
	 *
	 * @return флаг подключения GigaChat
	 */
	@GetMapping
	public StatusResponse status() {
		return new StatusResponse(properties.configured());
	}
}
