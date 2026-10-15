package ru.smartlearn.mentor.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP-вход в диалог наставника.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private final ChatService chatService;

	/**
	 * Подключает сервис сценария.
	 *
	 * @param chatService ведение сессии
	 */
	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	/**
	 * Принимает реплику и возвращает следующий шаг.
	 * Без {@code sessionId} открывается новая сессия.
	 *
	 * @param request идентификатор и текст
	 * @return реплика наставника и, в конце, отчёт
	 */
	@PostMapping
	public ChatResponse chat(@RequestBody ChatRequest request) {
		return chatService.chat(request.sessionId(), request.message(), request.action(), request.questionId());
	}
}
