package com.ailms.backend.domain.quiz.application;

/**
 * 테스트용 모델 답변 (가짜 모델이 돌려주는 글). 실제 AI가 만든 문제가 아니다.
 */
public final class QuizFixtures {

	private QuizFixtures() {
	}

	// 객관식·주관식·서술형 한 문제씩 (개념 NAT·VLAN). work면 상황·역할을 붙인다
	public static String threeTypes(boolean work) {
		String context = work ? "\"scenario\":\"중소기업 사내망과 방문객 Wi-Fi가 한 네트워크다\",\"role\":\"IT 컨설턴트\"," : "";
		return "{\"questions\":["
				+ "{\"type\":\"multiple_choice\"," + context + "\"concepts\":[\"NAT\"],\"text\":\"NAT의 주요 기능으로 가장 알맞은 것은?\","
				+ "\"choices\":[\"패킷을 암호화한다\",\"주소를 바꾼다\",\"내부 주소를 숨긴다\",\"경로를 계산한다\"],\"answerIndex\":1,"
				+ "\"explanation\":\"NAT는 주소를 변환한다\"},"
				+ "{\"type\":\"short_answer\"," + context + "\"concepts\":[\"nat\"],\"text\":\"공인 IP 하나를 여러 단말이 함께 쓰는 NAT 방식은?\","
				+ "\"acceptedAnswers\":[\"PAT\",\"NAPT\",\"pat\"],\"guide\":\"약어로 써도 된다\"},"
				+ "{\"type\":\"essay\"," + context + "\"concepts\":[\"NAT\",\"VLAN\"],\"text\":\"Static NAT와 PAT의 차이를 설명하시오.\","
				+ "\"rubric\":[{\"criterion\":\"차이 설명\",\"points\":4},{\"criterion\":\"활용 사례\",\"points\":2}],"
				+ "\"modelAnswer\":\"Static NAT는 일대일, PAT는 포트로 여러 단말을 구분한다.\"}"
				+ "]}";
	}

	/**
	 * 요청 글(QuizPrompt.user JSON)의 typeCounts·concepts에 맞춰 문제를 만드는 가짜 답. 실제 AI가 만든 문제가 아니다.
	 */
	public static String forRequest(String userJson) {
		try {
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(userJson);
			java.util.List<String> concepts = new java.util.ArrayList<>();
			root.get("concepts").forEach(node -> concepts.add(node.asText()));
			boolean work = "work".equals(root.path("mode").asText());
			String context = work ? "\"scenario\":\"사내망 상황\",\"role\":\"담당자\"," : "";
			StringBuilder out = new StringBuilder("{\"questions\":[");
			int n = 0;
			java.util.Iterator<java.util.Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> types =
					root.get("typeCounts").properties().iterator();
			while (types.hasNext()) {
				var entry = types.next();
				for (int i = 0; i < entry.getValue().asInt(); i++) {
					String concept = concepts.get(n % concepts.size());
					if (n > 0) {
						out.append(',');
					}
					out.append("{\"type\":\"").append(entry.getKey()).append("\",").append(context)
							.append("\"concepts\":[\"").append(concept).append("\"],\"text\":\"").append(concept)
							.append(" 문제 ").append(n + 1).append("\",");
					switch (entry.getKey()) {
						case "multiple_choice" -> out.append("\"choices\":[\"가\",\"나\",\"다\",\"라\"],\"answerIndex\":0}");
						case "short_answer" -> out.append("\"acceptedAnswers\":[\"정답\"]}");
						default -> out.append("\"rubric\":[{\"criterion\":\"기준1\",\"points\":2},")
								.append("{\"criterion\":\"기준2\",\"points\":2}],\"modelAnswer\":\"모범\"}");
					}
					n++;
				}
			}
			return out.append("]}").toString();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
