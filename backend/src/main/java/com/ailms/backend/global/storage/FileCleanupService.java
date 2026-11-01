package com.ailms.backend.global.storage;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DB 행을 지운 뒤 저장소 파일을 안전하게 지운다.
 *
 * <ol>
 * <li>자료 행을 지우는 트랜잭션 안에서 deleteAfterCommit으로 file_deletion_task 행을 함께 만든다.
 * 트랜잭션이 롤백되면 작업 행도 사라지고 파일은 그대로다 (DB가 가리키는 파일이 먼저 사라지는 일이 없다).</li>
 * <li>커밋되면 바로 파일을 지우고 작업 행을 지운다.</li>
 * <li>파일을 지우지 못하면 작업 행이 남고, retryPending이 app.storage.cleanup-retry-interval마다 다시 지운다
 * (지우지 못한 파일이 기록 없이 남지 않는다).</li>
 * </ol>
 */
@Slf4j
@Service
public class FileCleanupService {

	private final FileStorage fileStorage;
	private final FileDeletionTaskRepository taskRepository;
	private final ApplicationEventPublisher events;
	private final Clock clock;
	// 커밋이 끝난 뒤에 실행되므로 DB 작업은 새 트랜잭션에서 한다
	private final TransactionTemplate newTransaction;

	public FileCleanupService(FileStorage fileStorage, FileDeletionTaskRepository taskRepository,
			ApplicationEventPublisher events, Clock clock, PlatformTransactionManager transactionManager) {
		this.fileStorage = fileStorage;
		this.taskRepository = taskRepository;
		this.events = events;
		this.clock = clock;
		this.newTransaction = new TransactionTemplate(transactionManager);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** 지금 트랜잭션이 커밋되면 파일을 지운다. 롤백되면 아무것도 지우지 않는다. 트랜잭션 안에서만 부를 수 있다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void deleteAfterCommit(Collection<String> storageKeys) {
		if (storageKeys.isEmpty()) {
			return;
		}
		LocalDateTime now = LocalDateTime.now(clock);
		List<Long> taskIds = taskRepository.saveAll(
						storageKeys.stream().map(key -> new FileDeletionTask(key, now)).toList())
				.stream()
				.map(FileDeletionTask::getId)
				.toList();
		events.publishEvent(new FileDeletionRequested(taskIds));
	}

	/**
	 * 트랜잭션 밖에서 바로 지우지 못한 파일을 다음 정리 때 지우도록 작업만 남긴다
	 * (예: 파일을 저장한 뒤 DB 저장이 롤백됐는데 그 파일 삭제도 실패한 경우).
	 */
	public void deleteLater(String storageKey) {
		newTransaction.executeWithoutResult(status ->
				taskRepository.save(new FileDeletionTask(storageKey, LocalDateTime.now(clock))));
	}

	@TransactionalEventListener
	public void onDeletionRequested(FileDeletionRequested event) {
		// 요청은 이미 커밋됐으므로 여기서 난 오류로 응답을 실패시키지 않는다. 남은 작업은 retryPending이 처리한다
		try {
			process(event.taskIds());
		} catch (RuntimeException e) {
			log.warn("커밋 뒤 파일 정리를 끝내지 못해 다음 정리 때 다시 시도한다", e);
		}
	}

	/** 남아 있는 작업을 오래된 것부터 다시 시도한다. 이번에 지운 파일 수를 돌려준다. */
	@Scheduled(fixedDelayString = "${app.storage.cleanup-retry-interval}",
			initialDelayString = "${app.storage.cleanup-retry-interval}")
	public int retryPending() {
		List<Long> taskIds = newTransaction.execute(status -> taskRepository.findTop100ByOrderByIdAsc().stream()
				.map(FileDeletionTask::getId)
				.toList());
		return process(taskIds);
	}

	private int process(List<Long> taskIds) {
		List<FileDeletionTask> tasks = newTransaction.execute(status -> taskRepository.findAllById(taskIds));
		int deleted = 0;
		for (FileDeletionTask task : tasks) {
			if (attempt(task)) {
				deleted++;
			}
		}
		return deleted;
	}

	private boolean attempt(FileDeletionTask task) {
		try {
			fileStorage.delete(task.getStorageKey());
		} catch (RuntimeException e) {
			log.warn("파일을 지우지 못해 다음 정리 때 다시 시도한다 (작업 {}번)", task.getId(), e);
			newTransaction.executeWithoutResult(status -> taskRepository.findById(task.getId())
					.ifPresent(found -> found.recordFailure(LocalDateTime.now(clock))));
			return false;
		}
		// 여기서 실패해도 작업 행이 남을 뿐이고, 다음 정리 때 이미 없는 파일을 지우는 것은 문제가 없다
		newTransaction.executeWithoutResult(status -> taskRepository.deleteById(task.getId()));
		return true;
	}

	public record FileDeletionRequested(List<Long> taskIds) {
	}
}
