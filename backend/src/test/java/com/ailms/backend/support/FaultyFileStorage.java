package com.ailms.backend.support;

import com.ailms.backend.global.storage.FileStorage;
import com.ailms.backend.global.storage.FileStorageException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * 통합 테스트에서 실제 저장소(LocalFileStorage, 임시 폴더) 앞에 끼워 저장·삭제 실패를 흉내 낸다.
 * 테스트가 끝나면 reset()으로 되돌린다.
 */
public class FaultyFileStorage implements FileStorage {

	private final FileStorage delegate;
	private volatile boolean failStore;
	private volatile boolean failDelete;

	public FaultyFileStorage(FileStorage delegate) {
		this.delegate = delegate;
	}

	public void failStore(boolean fail) {
		this.failStore = fail;
	}

	public void failDelete(boolean fail) {
		this.failDelete = fail;
	}

	public void reset() {
		failStore = false;
		failDelete = false;
	}

	@Override
	public void store(String key, Path source) {
		if (failStore) {
			throw new FileStorageException("테스트: 저장 실패");
		}
		delegate.store(key, source);
	}

	@Override
	public InputStream open(String key) {
		return delegate.open(key);
	}

	@Override
	public boolean exists(String key) {
		return delegate.exists(key);
	}

	@Override
	public void delete(String key) {
		if (failDelete) {
			throw new FileStorageException("테스트: 삭제 실패");
		}
		delegate.delete(key);
	}
}
