package ru.smartlearn.mentor.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет, что проверка живости отвечает без кредов и сессии.
 */
class HealthControllerTest {

	/**
	 * Платформа получает 200 и короткий текст на {@code /health}.
	 */
	@Test
	void healthReturnsOk() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();
		mockMvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(content().string("ok"));
	}
}
