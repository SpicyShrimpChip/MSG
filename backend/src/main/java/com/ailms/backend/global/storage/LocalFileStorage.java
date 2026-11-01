package com.ailms.backend.global.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로컬 디스크 저장소. app.storage.local.root 폴더 아래에 key 경로 그대로 저장한다.
 * key 형식을 검사하고, 정리한 경로가 root 밖이면 거절해 경로 조작(../ 등)을 막는다.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

	private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]*(/[A-Za-z0-9][A-Za-z0-9_.-]*)*$");

	private final Path root;

	public LocalFileStorage(StorageProperties properties) {
		this.root = properties.local().root().toAbsolutePath().normalize();
		try {
			Files.createDirectories(root);
		} catch (IOException e) {
			throw new IllegalStateException("파일 저장 폴더를 만들지 못했다: " + root, e);
		}
	}

	@Override
	public void store(String key, Path source) {
		Path target = resolve(key);
		Path temp = null;
		try {
			Files.createDirectories(target.getParent());
			// 같은 폴더의 임시 파일에 다 쓴 뒤 이름만 바꾼다. 쓰다가 실패해도 반쯤 쓴 파일이 key에 남지 않는다
			temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
			Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
			if (Files.exists(target)) {
				throw new FileAlreadyExistsException(key);
			}
			Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
			temp = null;
		} catch (IOException e) {
			throw new FileStorageException("파일을 저장하지 못했다: " + key, e);
		} finally {
			deleteQuietly(temp);
		}
	}

	@Override
	public InputStream open(String key) {
		try {
			return Files.newInputStream(resolve(key));
		} catch (NoSuchFileException e) {
			throw new FileStorageException("파일이 없다: " + key, e);
		} catch (IOException e) {
			throw new FileStorageException("파일을 열지 못했다: " + key, e);
		}
	}

	@Override
	public boolean exists(String key) {
		return Files.isRegularFile(resolve(key));
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(resolve(key));
		} catch (IOException e) {
			throw new FileStorageException("파일을 지우지 못했다: " + key, e);
		}
	}

	private Path resolve(String key) {
		if (key == null || !KEY.matcher(key).matches()) {
			throw new FileStorageException("저장소 key 형식이 아니다: " + key);
		}
		Path path = root.resolve(key).normalize();
		if (!path.startsWith(root) || path.equals(root)) {
			throw new FileStorageException("저장 폴더 밖을 가리키는 key: " + key);
		}
		return path;
	}

	private static void deleteQuietly(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// 임시 파일이라 남아도 key와 겹치지 않는다(이름이 '.'으로 시작해 key 형식이 아님)
		}
	}
}
