package ru.smartlearn.mentor.dialog;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Сессия не найдена: клиент прислал неизвестный или уже потерянный id.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class SessionNotFoundException extends RuntimeException {

	/**
	 * Сообщает, какой идентификатор не найден.
	 *
	 * @param sessionId присланный идентификатор
	 */
	public SessionNotFoundException(String sessionId) {
		super("Сессия не найдена: " + sessionId);
	}
}
