package com.msg.backend.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 카카오 인가·API 서버를 흉내 내는 로컬 서버 (실제 카카오는 부르지 않는다).
 * /oauth/token, /v2/user/me, /v1/user/unlink에 답하고, 경로마다 호출 수·마지막 폼·Authorization 헤더를 기록한다.
 * 응답 상태·본문·지연은 테스트에서 바꾸고, 테스트마다 reset()으로 되돌린다.
 */
public class FakeKakaoServer implements AutoCloseable {

	public static final String TOKEN = "/oauth/token";
	public static final String ME = "/v2/user/me";
	public static final String UNLINK = "/v1/user/unlink";

	private final HttpServer server;
	private final Map<String, Reply> replies = new ConcurrentHashMap<>();
	private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
	private final Map<String, Map<String, String>> lastForms = new ConcurrentHashMap<>();
	private final Map<String, String> lastAuthorizations = new ConcurrentHashMap<>();
	private final Map<String, Runnable> onRequest = new ConcurrentHashMap<>();

	private FakeKakaoServer(HttpServer server) {
		this.server = server;
	}

	public static FakeKakaoServer start() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			FakeKakaoServer fake = new FakeKakaoServer(server);
			for (String path : new String[] {TOKEN, ME, UNLINK}) {
				server.createContext(path, fake::handle);
			}
			server.setExecutor(Executors.newCachedThreadPool(runnable -> {
				Thread thread = new Thread(runnable, "fake-kakao");
				thread.setDaemon(true);
				return thread;
			}));
			fake.reset();
			server.start();
			return fake;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	// 기본 응답: 토큰 발급 성공, 회원번호 4242·확인된 이메일·닉네임, 연결 끊기 성공
	public void reset() {
		calls.clear();
		lastForms.clear();
		lastAuthorizations.clear();
		onRequest.clear();
		replies.put(TOKEN, new Reply(200, "{\"access_token\":\"fake-kakao-access\",\"token_type\":\"bearer\"}",
				Duration.ZERO));
		user(4242L, "kakao@example.com", true, true, "카카오닉");
		replies.put(UNLINK, new Reply(200, "{\"id\":4242}", Duration.ZERO));
	}

	// 사용자 정보 응답을 정한다. email이 null이면 이메일 항목을 빼고(동의 안 함), nickname이 null이면 프로필을 뺀다
	public void user(long id, String email, boolean emailValid, boolean emailVerified, String nickname) {
		StringBuilder account = new StringBuilder("{");
		if (email != null) {
			account.append("\"has_email\":true,\"email_needs_agreement\":false,")
					.append("\"is_email_valid\":").append(emailValid).append(',')
					.append("\"is_email_verified\":").append(emailVerified).append(',')
					.append("\"email\":\"").append(email).append('"');
		} else {
			account.append("\"has_email\":true,\"email_needs_agreement\":true");
		}
		if (nickname != null) {
			account.append(",\"profile\":{\"nickname\":\"").append(nickname).append("\"}");
		}
		account.append('}');
		replies.put(ME, new Reply(200, "{\"id\":" + id + ",\"kakao_account\":" + account + "}", Duration.ZERO));
	}

	public void reply(String path, int status, String body) {
		replies.put(path, new Reply(status, body, Duration.ZERO));
	}

	public void delay(String path, Duration delay) {
		Reply reply = replies.get(path);
		replies.put(path, new Reply(reply.status(), reply.body(), delay));
	}

	public int calls(String path) {
		AtomicInteger count = calls.get(path);
		return count == null ? 0 : count.get();
	}

	public int totalCalls() {
		return calls.values().stream().mapToInt(AtomicInteger::get).sum();
	}

	public Map<String, String> lastForm(String path) {
		return lastForms.getOrDefault(path, Map.of());
	}

	public String lastAuthorization(String path) {
		return lastAuthorizations.get(path);
	}

	// 요청을 받는 순간(응답하기 전) 할 일. 예: 그때 앱이 빌려 간 DB 연결 수 재기. reset()이 지운다
	public void onRequest(String path, Runnable action) {
		onRequest.put(path, action);
	}

	@Override
	public void close() {
		server.stop(0);
	}

	private void handle(HttpExchange exchange) throws IOException {
		String path = exchange.getHttpContext().getPath();
		calls.computeIfAbsent(path, key -> new AtomicInteger()).incrementAndGet();
		lastForms.put(path, parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
		String authorization = exchange.getRequestHeaders().getFirst("Authorization");
		if (authorization != null) {
			lastAuthorizations.put(path, authorization);
		}
		Runnable action = onRequest.get(path);
		if (action != null) {
			action.run();
		}
		Reply reply = replies.get(path);
		if (!reply.delay().isZero()) {
			try {
				Thread.sleep(reply.delay().toMillis());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
		try {
			exchange.sendResponseHeaders(reply.status(), bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		} catch (IOException ignored) {
			// 클라이언트가 시간 초과로 먼저 끊은 경우
		} finally {
			exchange.close();
		}
	}

	private static Map<String, String> parseForm(String body) {
		Map<String, String> form = new HashMap<>();
		if (body.isEmpty()) {
			return form;
		}
		for (String pair : body.split("&")) {
			int eq = pair.indexOf('=');
			String key = eq < 0 ? pair : pair.substring(0, eq);
			String value = eq < 0 ? "" : pair.substring(eq + 1);
			form.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
		}
		return form;
	}

	private record Reply(int status, String body, Duration delay) {
	}
}
