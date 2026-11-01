package com.ailms.backend.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// 임시 폴더만 쓰는 단위 테스트
class LocalFileStorageTest {

	@TempDir
	Path temp;

	private Path root;
	private Path source;
	private LocalFileStorage storage;

	@BeforeEach
	void setUp() throws IOException {
		root = temp.resolve("storage");
		source = Files.writeString(temp.resolve("source.txt"), "공부 노트", StandardCharsets.UTF_8);
		storage = new LocalFileStorage(new StorageProperties(
				"local", new StorageProperties.Local(root), Duration.ofMinutes(10)));
	}

	@Test
	void 저장한_파일을_열고_지울_수_있다() throws IOException {
		storage.store("materials/1/abc.txt", source);

		assertThat(storage.exists("materials/1/abc.txt")).isTrue();
		assertThat(root.resolve("materials/1/abc.txt")).hasContent("공부 노트");
		try (InputStream in = storage.open("materials/1/abc.txt")) {
			assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("공부 노트");
		}

		storage.delete("materials/1/abc.txt");
		assertThat(storage.exists("materials/1/abc.txt")).isFalse();
	}

	@Test
	void 없는_파일을_지워도_오류가_나지_않는다() {
		storage.delete("materials/1/none.txt");
		storage.delete("materials/1/none.txt");
	}

	@Test
	void 같은_key가_있으면_덮어쓰지_않고_실패한다() throws IOException {
		storage.store("materials/1/abc.txt", source);
		Path other = Files.writeString(temp.resolve("other.txt"), "다른 내용");

		assertThatThrownBy(() -> storage.store("materials/1/abc.txt", other))
				.isInstanceOf(FileStorageException.class);
		assertThat(root.resolve("materials/1/abc.txt")).hasContent("공부 노트");
	}

	@Test
	void 저장에_실패하면_임시_파일을_남기지_않는다() {
		assertThatThrownBy(() -> storage.store("materials/1/abc.txt", temp.resolve("missing.txt")))
				.isInstanceOf(FileStorageException.class);

		try (Stream<Path> files = Files.walk(root)) {
			assertThat(files.filter(Files::isRegularFile)).isEmpty();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"../escape.txt", "materials/../../escape.txt", "/etc/passwd", "materials//a.txt",
			".hidden", "materials/.upload-1.tmp", "materials\\a.txt", "materials/a b.txt", ""})
	void 형식에_맞지_않거나_밖을_가리키는_key는_거절한다(String key) {
		assertThatThrownBy(() -> storage.store(key, source)).isInstanceOf(FileStorageException.class);
		assertThatThrownBy(() -> storage.delete(key)).isInstanceOf(FileStorageException.class);
		assertThat(temp.resolve("escape.txt")).doesNotExist();
	}
}
