package ru.smartlearn.mentor.dialog;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранит сессии в памяти процесса.
 * Для хакатона этого достаточно: отдельная база не нужна.
 */
@Component
public class SessionStore {

	private final ConcurrentHashMap<String, MentorSession> sessions = new ConcurrentHashMap<>();

	/**
	 * Открывает новую пустую сессию.
	 *
	 * @return сессия с новым идентификатором
	 */
	public MentorSession create() {
		MentorSession session = new MentorSession(UUID.randomUUID().toString());
		sessions.put(session.id(), session);
		return session;
	}

	/**
	 * Находит сессию по идентификатору.
	 *
	 * @param sessionId идентификатор из предыдущего ответа
	 * @return живая сессия
	 * @throws SessionNotFoundException если идентификатор неизвестен
	 */
	public MentorSession require(String sessionId) {
		MentorSession session = sessions.get(sessionId);
		if (session == null) {
			throw new SessionNotFoundException(sessionId);
		}
		return session;
	}
}
