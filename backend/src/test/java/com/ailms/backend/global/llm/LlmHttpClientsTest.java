package com.ailms.backend.global.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 제공사별 HTTP 요청 모양과 응답 읽기, 오류 구분. 실제 제공사가 아니라 이 PC 안의 가짜 HTTP 서버로 확인한다
 * (실제 연결 여부는 API 키가 생긴 뒤 따로 확인해야 한다). 요청 형식은 각 제공사 공개 API 문서 기준이다.
 */
class LlmHttpClientsTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private HttpServer server;
	private final AtomicReference<Recorded> last = new AtomicReference<>();
	private volatile int status = 200;
	private volatile String responseBody = "{}";
	private volatile long delayMs;

	record Recorded(String path, Map<String, String> headers, JsonNode body) {
	}

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			Map<String, String> headers = new java.util.HashMap<>();
			exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
			last.set(new Recorded(exchange.getRequestURI().getPath(), headers,
					objectMapper.readTree(exchange.getRequestBody().readAllBytes())));
			try {
				Thread.sleep(delayMs);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
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

	private LlmProperties properties(Duration timeout) {
		String url = "http://127.0.0.1:" + server.getAddress().getPort();
		LlmProperties.Provider provider = new LlmProperties.Provider("test-key", "test-model", url);
		return new LlmProperties(timeout, 0, Duration.ZERO, provider, provider, provider);
	}

	private final LlmRequest request = new LlmRequest("지시문", "대화 글", 500);

	@Test
	void GPT는_chat_completions에_Bearer_키와_JSON_응답_형식을_보낸다() {
		responseBody = """
				{"choices":[{"message":{"role":"assistant","content":"{\\"concepts\\":[]}"}}],
				 "usage":{"prompt_tokens":12,"completion_tokens":5}}""";

		LlmResponse response = new OpenAiClient(properties(Duration.ofSeconds(5)), objectMapper).complete(request);

		Recorded sent = last.get();
		assertThat(sent.path()).isEqualTo("/v1/chat/completions");
		assertThat(sent.headers().get("authorization")).isEqualTo("Bearer test-key");
		assertThat(sent.body().path("model").asText()).isEqualTo("test-model");
		assertThat(sent.body().path("messages").path(0).path("role").asText()).isEqualTo("system");
		assertThat(sent.body().path("messages").path(0).path("content").asText()).isEqualTo("지시문");
		assertThat(sent.body().path("messages").path(1).path("content").asText()).isEqualTo("대화 글");
		assertThat(sent.body().path("response_format").path("type").asText()).isEqualTo("json_object");
		assertThat(sent.body().path("max_completion_tokens").asInt()).isEqualTo(500);
		assertThat(response).isEqualTo(new LlmResponse("{\"concepts\":[]}", 12, 5));
	}

	@Test
	void Claude는_messages에_x_api_key와_버전_헤더를_보내고_text_블록을_이어_붙인다() {
		responseBody = """
				{"content":[{"type":"text","text":"{\\"concepts\\""},{"type":"text","text":":[]}"}],
				 "usage":{"input_tokens":20,"output_tokens":7}}""";

		LlmResponse response = new AnthropicClient(properties(Duration.ofSeconds(5)), objectMapper).complete(request);

		Recorded sent = last.get();
		assertThat(sent.path()).isEqualTo("/v1/messages");
		assertThat(sent.headers().get("x-api-key")).isEqualTo("test-key");
		assertThat(sent.headers().get("anthropic-version")).isEqualTo(AnthropicClient.API_VERSION);
		assertThat(sent.body().path("system").path(0).path("text").asText()).isEqualTo("지시문");
		assertThat(sent.body().path("system").path(0).path("cache_control").path("type").asText())
				.isEqualTo("ephemeral");
		assertThat(sent.body().path("messages").path(0).path("content").asText()).isEqualTo("대화 글");
		assertThat(sent.body().path("max_tokens").asInt()).isEqualTo(500);
		assertThat(response).isEqualTo(new LlmResponse("{\"concepts\":[]}", 20, 7));
	}

	@Test
	void Claude_입력_토큰은_캐시_쓰기_읽기를_더해_입력_전체로_센다() {
		responseBody = """
				{"content":[{"type":"text","text":"{}"}],
				 "usage":{"input_tokens":20,"cache_creation_input_tokens":0,"cache_read_input_tokens":900,
				 "output_tokens":7}}""";

		LlmResponse response = new AnthropicClient(properties(Duration.ofSeconds(5)), objectMapper).complete(request);

		assertThat(response).isEqualTo(new LlmResponse("{}", 920, 7));
	}

	@Test
	void Gemini는_키를_주소가_아닌_헤더로_보내고_JSON_응답_형식을_지정한다() {
		responseBody = """
				{"candidates":[{"content":{"parts":[{"text":"{\\"concepts\\":[]}"}]}}],
				 "usageMetadata":{"promptTokenCount":30,"candidatesTokenCount":9}}""";

		LlmResponse response = new GeminiClient(properties(Duration.ofSeconds(5)), objectMapper).complete(request);

		Recorded sent = last.get();
		assertThat(sent.path()).isEqualTo("/v1beta/models/test-model:generateContent");
		assertThat(sent.headers().get("x-goog-api-key")).isEqualTo("test-key");
		assertThat(sent.body().path("systemInstruction").path("parts").path(0).path("text").asText()).isEqualTo("지시문");
		assertThat(sent.body().path("contents").path(0).path("parts").path(0).path("text").asText()).isEqualTo("대화 글");
		assertThat(sent.body().path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
		assertThat(response).isEqualTo(new LlmResponse("{\"concepts\":[]}", 30, 9));
	}

	@Test
	void 응답_상태별로_다시_부를지_구분한다() {
		OpenAiClient client = new OpenAiClient(properties(Duration.ofSeconds(5)), objectMapper);
		Map<Integer, LlmException.Code> expected = Map.of(
				429, LlmException.Code.RATE_LIMITED,
				401, LlmException.Code.AUTH_FAILED,
				400, LlmException.Code.REJECTED,
				503, LlmException.Code.SERVER_ERROR);
		expected.forEach((code, expectedCode) -> {
			status = code;
			responseBody = "{\"error\":{\"message\":\"secret detail\"}}";
			assertThatThrownBy(() -> client.complete(request))
					.isInstanceOfSatisfying(LlmException.class, e -> {
						assertThat(e.getCode()).isEqualTo(expectedCode);
						// 응답 본문은 예외 메시지에 넣지 않는다
						assertThat(e.getMessage()).doesNotContain("secret detail");
					});
		});
		assertThat(LlmException.Code.RATE_LIMITED.retryable()).isTrue();
		assertThat(LlmException.Code.AUTH_FAILED.retryable()).isFalse();
	}

	@Test
	void 시간_안에_답이_없으면_TIMEOUT() {
		delayMs = 1500;
		AnthropicClient client = new AnthropicClient(properties(Duration.ofMillis(300)), objectMapper);

		assertThatThrownBy(() -> client.complete(request))
				.isInstanceOfSatisfying(LlmException.class,
						e -> assertThat(e.getCode()).isEqualTo(LlmException.Code.TIMEOUT));
	}

	@Test
	void 답변_글이_없는_응답은_BAD_RESPONSE() {
		responseBody = "{\"candidates\":[]}";
		GeminiClient client = new GeminiClient(properties(Duration.ofSeconds(5)), objectMapper);

		assertThatThrownBy(() -> client.complete(request))
				.isInstanceOfSatisfying(LlmException.class,
						e -> assertThat(e.getCode()).isEqualTo(LlmException.Code.BAD_RESPONSE));
	}

	@Test
	void 키나_모델이_없으면_미설정이고_부르지_않는다() {
		LlmProperties.Provider noKey = new LlmProperties.Provider("", "test-model", null);
		LlmProperties.Provider noModel = new LlmProperties.Provider("key", " ", null);
		LlmProperties properties = new LlmProperties(Duration.ofSeconds(5), 0, Duration.ZERO, noKey, noModel, noKey);

		OpenAiClient gpt = new OpenAiClient(properties, objectMapper);
		AnthropicClient claude = new AnthropicClient(properties, objectMapper);
		assertThat(gpt.isConfigured()).isFalse();
		assertThat(claude.isConfigured()).isFalse();
		assertThat(claude.model()).isNull();
		assertThatThrownBy(() -> gpt.complete(request)).isInstanceOf(IllegalStateException.class);
		assertThat(last.get()).isNull();
	}
}
