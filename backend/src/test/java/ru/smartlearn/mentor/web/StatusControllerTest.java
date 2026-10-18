package ru.smartlearn.mentor.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет, что без кредов интерфейс видит GigaChat как отключённый.
 * Пустые свойства перекрывают переменные сборки, иначе статус на CI становится true.
 */
@SpringBootTest(properties = {
		"gigachat.client-id=",
		"gigachat.scope=",
		"gigachat.authorization-key="
})
@AutoConfigureMockMvc
class StatusControllerTest {

	@Autowired
	private MockMvc mockMvc;

	/**
	 * Без Client ID, Scope и ключа статус честно говорит, что GigaChat не подключён.
	 */
	@Test
	void reportsDisconnectedWithoutCredentials() throws Exception {
		mockMvc.perform(get("/api/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.gigachatConnected").value(false));
	}
}
