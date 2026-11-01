package com.ailms.backend.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * 통합 테스트용 파일 저장소. 실행할 때마다 새 임시 폴더를 저장소로 쓰고, JVM이 끝나면 지운다.
 * 개발용 업로드 폴더(backend/uploads)에는 손대지 않는다. 주기 정리 작업도 꺼서 테스트가 직접 부른다.
 */
public class TestStorage implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		Path root = createRoot();
		context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testStorage", Map.of(
				"app.storage.type", "local",
				"app.storage.local.root", root.toString(),
				"app.scheduling.enabled", "false")));
	}

	private static Path createRoot() {
		try {
			Path root = Files.createTempDirectory("ai-lms-test-storage-");
			Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(root)));
			return root;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void deleteRecursively(Path root) {
		try (Stream<Path> paths = Files.walk(root)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		} catch (IOException ignored) {
			// 임시 폴더라 남아도 운영체제가 정리한다
		}
	}
}
