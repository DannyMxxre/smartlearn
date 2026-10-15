package ru.smartlearn.mentor.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.smartlearn.mentor.dialog.EmptyMessageException;
import ru.smartlearn.mentor.dialog.SessionNotFoundException;

import java.util.Map;

/**
 * Превращает ошибки сценария в короткие JSON-ответы.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	/**
	 * Сообщает, что сессия неизвестна.
	 *
	 * @param exception ошибка поиска сессии
	 * @return статус 404 и текст
	 */
	@ExceptionHandler(SessionNotFoundException.class)
	public ResponseEntity<Map<String, String>> missingSession(SessionNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", exception.getMessage()));
	}

	/**
	 * Сообщает, что прислали пустую реплику.
	 *
	 * @param exception ошибка пустого сообщения
	 * @return статус 400 и текст
	 */
	@ExceptionHandler(EmptyMessageException.class)
	public ResponseEntity<Map<String, String>> emptyMessage(EmptyMessageException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", exception.getMessage()));
	}
}
