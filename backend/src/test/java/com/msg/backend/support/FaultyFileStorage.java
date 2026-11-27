package com.msg.backend.support;

import com.msg.backend.global.storage.FileStorage;
import com.msg.backend.global.storage.FileStorageException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 통합 테스트에서 실제 저장소(LocalFileStorage, 임시 폴더) 앞에 끼워 저장·삭제 실패를 흉내 낸다.
 * 다음 한 번의 읽기(open)를 멈춰 두고 그동안 다른 요청을 보낼 수도 있다(pauseNextOpen). 테스트가 끝나면 reset()으로 되돌린다.
 */
public class FaultyFileStorage implements FileStorage {

	private final FileStorage delegate;
	private volatile boolean failStore;
	private volatile boolean failDelete;
	private final AtomicReference<Pause> nextOpen = new AtomicReference<>();
	private final AtomicInteger opens = new AtomicInteger();

	// entered: 읽기가 멈춘 순간 내려간다, release: 내려가면 읽기를 이어 간다
	private record Pause(CountDownLatch entered, CountDownLatch release) {
	}

	public FaultyFileStorage(FileStorage delegate) {
		this.delegate = delegate;
	}

	public void failStore(boolean fail) {
		this.failStore = fail;
	}

	public void failDelete(boolean fail) {
		this.failDelete = fail;
	}

	/** 다음 open 한 번을 release가 내려갈 때까지(최대 30초) 멈춘다. 멈추면 entered를 내린다 */
	public void pauseNextOpen(CountDownLatch entered, CountDownLatch release) {
		nextOpen.set(new Pause(entered, release));
	}

	public void reset() {
		failStore = false;
		failDelete = false;
		nextOpen.set(null);
		opens.set(0);
	}

	/** reset 뒤 open을 부른 수 (자료 본문을 몇 번 읽었는지) */
	public int openCount() {
		return opens.get();
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
		opens.incrementAndGet();
		Pause pause = nextOpen.getAndSet(null);
		if (pause != null) {
			pause.entered().countDown();
			try {
				pause.release().await(30, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
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
