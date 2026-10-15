package ru.smartlearn.mentor.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.smartlearn.mentor.config.YandexProperties;

/**
 * Сообщает интерфейсу, подключён ли YandexGPT.
 */
@RestController
@RequestMapping("/api/status")
public class StatusController {

	private final YandexProperties properties;

	/**
	 * Берёт настройки облака, чтобы проверить наличие ключа.
	 *
	 * @param properties ключ и каталог YandexGPT
	 */
	public StatusController(YandexProperties properties) {
		this.properties = properties;
	}

	/**
	 * Возвращает, заданы ли ключ и каталог.
	 * Сам ключ в ответ не входит.
	 *
	 * @return флаг подключения YandexGPT
	 */
	@GetMapping
	public StatusResponse status() {
		return new StatusResponse(properties.configured());
	}
}
