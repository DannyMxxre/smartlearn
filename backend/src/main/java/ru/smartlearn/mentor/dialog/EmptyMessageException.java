package ru.smartlearn.mentor.dialog;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Пустое сообщение пришло после того, как диалог уже начался.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class EmptyMessageException extends RuntimeException {

	/**
	 * Создаёт ошибку с понятным текстом для клиента.
	 */
	public EmptyMessageException() {
		super("Сообщение пустое");
	}
}
