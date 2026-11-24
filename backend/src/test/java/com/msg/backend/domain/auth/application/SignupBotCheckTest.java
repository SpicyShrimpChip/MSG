package com.msg.backend.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.backend.global.error.BusinessException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

// Cloudflare 확인 서버 대신 로컬 서버를 띄워 확인한다 (실제 Cloudflare는 부르지 않는다)
class SignupBotCheckTest {

	private HttpServer server;
	private final AtomicReference<Map<String, String>> lastForm = new AtomicReference<>();
	private final AtomicInteger calls = new AtomicInteger();
	private volatile String responseBody = "{\"success\":true}";

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/siteverify", exchange -> {
			calls.incrementAndGet();
			lastForm.set(parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
			byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void 비밀_키가_없으면_확인하지_않는다() {
		SignupBotCheck check = check("");

		check.verify(null);

		assertThat(calls).hasValue(0);
	}

	@Test
	void 토큰이_없으면_400이고_확인_서버를_부르지_않는다() {
		SignupBotCheck check = check("secret");

		assertThatThrownBy(() -> check.verify(" "))
				.isInstanceOf(BusinessException.class)
				.hasMessage(SignupBotCheck.MISSING_MESSAGE)
				.extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(calls).hasValue(0);
	}

	@Test
	void 확인에_성공하면_통과하고_비밀_키와_토큰을_폼으로_보낸다() {
		SignupBotCheck check = check("secret-value");

		check.verify("token-value");

		assertThat(lastForm.get()).containsEntry("secret", "secret-value").containsEntry("response", "token-value");
	}

	@Test
	void 확인에_실패하면_400() {
		responseBody = "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}";
		SignupBotCheck check = check("secret");

		assertThatThrownBy(() -> check.verify("bad"))
				.isInstanceOf(BusinessException.class)
				.hasMessage(SignupBotCheck.FAILED_MESSAGE)
				.extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void 확인_서버에_닿지_못하면_503이고_통과시키지_않는다() {
		SignupBotCheck check = new SignupBotCheck(
				new TurnstileProperties("secret", "http://127.0.0.1:1/siteverify", Duration.ofSeconds(1), false));

		assertThatThrownBy(() -> check.verify("token"))
				.isInstanceOf(BusinessException.class)
				.hasMessage(SignupBotCheck.UNAVAILABLE_MESSAGE)
				.extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
	}

	private SignupBotCheck check(String secret) {
		String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify";
		return new SignupBotCheck(new TurnstileProperties(secret, url, Duration.ofSeconds(2), false));
	}

	private static Map<String, String> parseForm(String body) {
		Map<String, String> form = new HashMap<>();
		for (String pair : body.split("&")) {
			String[] kv = pair.split("=", 2);
			form.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
					kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
		}
		return form;
	}
}
