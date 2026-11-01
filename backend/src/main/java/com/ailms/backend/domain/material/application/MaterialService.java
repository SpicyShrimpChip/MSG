package com.ailms.backend.domain.material.application;

import com.ailms.backend.domain.course.dao.CourseRepository;
import com.ailms.backend.domain.material.application.MaterialFileInspector.InspectedFile;
import com.ailms.backend.domain.material.dao.MaterialRepository;
import com.ailms.backend.domain.material.dto.MaterialDetailResponse;
import com.ailms.backend.domain.material.dto.MaterialSummaryResponse;
import com.ailms.backend.domain.material.dto.MaterialUploadResponse;
import com.ailms.backend.domain.material.entity.Material;
import com.ailms.backend.global.error.BusinessException;
import com.ailms.backend.global.storage.FileCleanupService;
import com.ailms.backend.global.storage.FileStorage;
import com.ailms.backend.global.storage.FileStorageException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 학습자료 올리기·조회·삭제. 사용자 id는 토큰의 값만 받고, 남의 자료·과목은 없는 것과 같게 404로 응답한다.
 *
 * <p>파일과 DB가 어긋나지 않게 하는 순서:
 * <ul>
 * <li>올리기: 검사 → 저장소에 저장 → DB 행 저장. DB 저장이나 커밋이 실패하면 방금 저장한 파일을 지운다.</li>
 * <li>삭제: DB 행 삭제와 파일 삭제 작업(file_deletion_task)을 한 트랜잭션에 남기고, 커밋된 뒤 파일을 지운다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialService {

	static final String NOT_FOUND_MESSAGE = "자료를 찾을 수 없어요.";
	static final String COURSE_NOT_FOUND_MESSAGE = "과목을 찾을 수 없어요.";
	static final String STORE_FAILED_MESSAGE = "파일을 저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.";

	private final MaterialRepository materialRepository;
	private final CourseRepository courseRepository;
	private final MaterialFileInspector fileInspector;
	private final FileStorage fileStorage;
	private final FileCleanupService fileCleanupService;
	private final Clock clock;

	public List<MaterialSummaryResponse> getMaterials(Long userId) {
		return materialRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
				.map(MaterialSummaryResponse::from)
				.toList();
	}

	public MaterialDetailResponse getMaterial(Long userId, Long id) {
		return MaterialDetailResponse.from(find(userId, id));
	}

	/**
	 * @param courseId 연결할 과목. null이면 과목 없이 올린다. 있으면 로그인한 사용자의 과목이어야 한다
	 */
	@Transactional
	public MaterialUploadResponse upload(Long userId, Long courseId, String rawTitle, MultipartFile file) {
		String title = title(rawTitle);
		if (courseId != null && !courseRepository.existsByIdAndUserId(courseId, userId)) {
			throw new BusinessException(HttpStatus.NOT_FOUND, COURSE_NOT_FOUND_MESSAGE);
		}
		Path received = receive(file);
		try {
			InspectedFile inspected = fileInspector.inspect(file.getOriginalFilename(), received);
			// 저장 위치는 사용자가 보낸 파일 이름을 쓰지 않고 서버가 만든다
			String storageKey = "materials/" + userId + "/" + UUID.randomUUID() + "." + inspected.format().extension();
			store(storageKey, received);
			Material material = materialRepository.save(Material.upload(userId, courseId, title,
					inspected.fileName(), inspected.format(), inspected.size(), storageKey, LocalDateTime.now(clock)));
			return MaterialUploadResponse.from(material);
		} finally {
			deleteQuietly(received);
		}
	}

	@Transactional
	public void delete(Long userId, Long id) {
		deleteAll(List.of(find(userId, id)));
	}

	/**
	 * 과목을 지울 때 그 과목의 자료를 모두 지운다. 과목 삭제와 같은 트랜잭션에서만 부른다
	 * (과목 삭제가 롤백되면 자료와 파일도 그대로 남는다).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void deleteAllInCourse(Long userId, Long courseId) {
		deleteAll(materialRepository.findAllByUserIdAndCourseId(userId, courseId));
	}

	// 나중에 붙을 AI 결과(요약·개념·퀴즈 등)는 material을 외래 키(ON DELETE CASCADE)로 가지므로 행과 함께 지워진다.
	// AI가 만든 파일이 생기면 그 storage key도 여기서 deleteAfterCommit에 함께 넘긴다
	private void deleteAll(List<Material> materials) {
		if (materials.isEmpty()) {
			return;
		}
		materialRepository.deleteAll(materials);
		materialRepository.flush();
		fileCleanupService.deleteAfterCommit(materials.stream()
				.map(Material::getStorageKey)
				.filter(Objects::nonNull)
				.toList());
	}

	private Material find(Long userId, Long id) {
		return materialRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
	}

	private void store(String storageKey, Path content) {
		try {
			fileStorage.store(storageKey, content);
		} catch (FileStorageException e) {
			log.error("자료 원본을 저장하지 못했다", e);
			throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, STORE_FAILED_MESSAGE);
		}
		// 이 뒤에 DB 저장이나 커밋이 실패하면 DB가 모르는 파일이 남지 않게 지운다
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					discard(storageKey);
				}
			}
		});
	}

	private void discard(String storageKey) {
		try {
			fileStorage.delete(storageKey);
		} catch (RuntimeException e) {
			log.warn("롤백된 업로드의 파일을 바로 지우지 못해 정리 작업으로 넘긴다", e);
			try {
				fileCleanupService.deleteLater(storageKey);
			} catch (RuntimeException again) {
				log.error("롤백된 업로드의 파일을 정리 작업으로도 넘기지 못했다: {}", storageKey, again);
			}
		}
	}

	// 받은 파일을 임시 파일로 옮겨 검사·저장에 쓴다 (PPTX 검사는 파일 끝의 ZIP 목록을 읽어야 한다)
	private static Path receive(MultipartFile file) {
		try {
			Path temp = Files.createTempFile("material-", ".upload");
			try (InputStream in = file.getInputStream()) {
				Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				deleteQuietly(temp);
				throw e;
			}
			return temp;
		} catch (IOException e) {
			throw new UncheckedIOException("올린 파일을 받지 못했다", e);
		}
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			log.warn("업로드 임시 파일을 지우지 못했다", e);
		}
	}

	// 앞뒤 공백을 지우고 안쪽 공백은 한 칸으로 줄인다 (프론트 MaterialUploadPage와 같은 정리)
	private static String title(String raw) {
		String title = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
		if (title.isEmpty()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "자료 제목을 입력해 주세요.");
		}
		if (title.length() > Material.TITLE_MAX_LENGTH) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "자료 제목은 60자까지 넣을 수 있어요.");
		}
		return title;
	}
}
