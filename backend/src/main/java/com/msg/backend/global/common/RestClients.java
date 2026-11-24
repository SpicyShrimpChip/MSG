package com.msg.backend.global.common;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 외부 서버(AI 제공사·카카오·Turnstile)를 부를 RestClient. JDK HttpClient에 연결 시간 제한과 응답 시간 제한을 건다.
 * 주소(baseUrl) 등은 받은 빌더에 부르는 쪽이 더한다.
 */
public final class RestClients {

	private RestClients() {
	}

	public static RestClient.Builder withTimeouts(Duration connectTimeout, Duration readTimeout) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder().requestFactory(requestFactory);
	}
}
