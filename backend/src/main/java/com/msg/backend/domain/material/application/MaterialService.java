package com.msg.backend.domain.material.application;

import com.msg.backend.domain.course.dao.CourseRepository;
import com.msg.backend.domain.material.application.MaterialFileInspector.InspectedFile;
import com.msg.backend.domain.material.dao.MaterialRepository;
import com.msg.backend.domain.material.dto.MaterialDetailResponse;
import com.msg.backend.domain.material.dto.MaterialSummaryResponse;
import com.msg.backend.domain.material.dto.MaterialUploadResponse;
import com.msg.backend.domain.material.entity.Material;
import com.msg.backend.domain.quiz.dao.QuizRepository;
import com.msg.backend.domain.quiz.dao.QuizRepository.MaterialQuizCount;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.global.error.BusinessException;
import com.msg.backend.global.storage.FileCleanupService;
import com.msg.backend.global.storage.FileStorage;
import com.msg.backend.global.storage.FileStorageException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
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
 *
 * <p>같은 올리기가 두 번 오는 경우(더블클릭·연타·응답을 못 받은 재시도, #210): 화면이 올리기마다 만든 요청 키를 함께 보내면
 * 같은 사용자·같은 키의 두 번째 요청은 파일을 저장하지 않고 이미 올린 자료를 돌려준다. 동시에 온 경우는 사용자 행을 잠가
 * 차례로 처리하고, 그래도 (user_id, request_key) UNIQUE에 걸리면 트랜잭션이 롤백되며 방금 저장한 파일을 지운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialService {

	static final String STORE_FAILED_MESSAGE = "파일을 저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.";

	static final String REQUEST_KEY_TOO_LONG_MESSAGE = "요청 키가 너무 길어요.";

	private final MaterialRepository materialRepository;
	private final UserRepository userRepository;
	private final CourseRepository courseRepository;
	private final QuizRepository quizRepository;
	private final com.msg.backend.domain.material.dao.MaterialConceptRepository conceptRepository;
	private final MaterialFileInspector fileInspector;
	private final FileStorage fileStorage;
	private final FileCleanupService fileCleanupService;
	private final Clock clock;

	public List<MaterialSummaryResponse> getMaterials(Long userId) {
		Map<Long, Long> quizCounts = quizRepository.countByMaterialOfUser(userId).stream()
				.collect(Collectors.toMap(MaterialQuizCount::getMaterialId, MaterialQuizCount::getQuizCount));
		return materialRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
				.map(material -> MaterialSummaryResponse.from(material,
						quizCounts.getOrDefault(material.getId(), 0L).intValue()))
				.toList();
	}

	public MaterialDetailResponse getMaterial(Long userId, Long id) {
		Material material = materialRepository.getOwned(id, userId);
		return MaterialDetailResponse.from(material,
				(int) quizRepository.countByUserIdAndMaterialId(userId, material.getId()),
				conceptRepository.findAllByMaterialIdAndUserIdOrderBySortOrderAsc(material.getId(), userId));
	}

	/**
	 * 올리기 결과. reused면 같은 요청 키로 이미 올린 자료를 그대로 돌려준 것이다(새로 저장하지 않음)
	 */
	public record Uploaded(MaterialUploadResponse material, boolean reused) {
	}

	/**
	 * @param courseId 연결할 과목. null이면 과목 없이 올린다. 있으면 로그인한 사용자의 과목이어야 한다
	 * @param rawRequestKey 화면이 올리기마다 만든 요청 키(선택, 64자까지). 같은 키로 이미 올린 내 자료가 있으면 그 자료를 돌려준다
	 */
	@Transactional
	public Uploaded upload(Long userId, Long courseId, String rawTitle, String rawRequestKey, MultipartFile file) {
		String title = title(rawTitle);
		String requestKey = requestKey(rawRequestKey);
		if (requestKey != null) {
			// 사용자 행을 잠가 같은 사용자의 키 있는 올리기를 차례로 처리한다.
			// 같은 키로 동시에 온 요청은 앞 요청이 커밋될 때까지 기다렸다가 그 자료를 찾아 돌려준다 (파일을 두 번 저장하지 않는다)
			userRepository.lockLoginUser(userId);
			var existing = materialRepository.findByUserIdAndRequestKey(userId, requestKey);
			if (existing.isPresent()) {
				return new Uploaded(MaterialUploadResponse.from(existing.get()), true);
			}
		}
		if (courseId != null) {
			courseRepository.checkOwned(courseId, userId);
		}
		Path received = receive(file);
		try {
			InspectedFile inspected = fileInspector.inspect(file.getOriginalFilename(), received);
			// 저장 위치는 사용자가 보낸 파일 이름을 쓰지 않고 서버가 만든다
			String storageKey = "materials/" + userId + "/" + UUID.randomUUID() + "." + inspected.format().extension();
			store(storageKey, received);
			Material material = materialRepository.save(Material.upload(userId, courseId, title,
					inspected.fileName(), inspected.format(), inspected.size(), storageKey, requestKey,
					LocalDateTime.now(clock)));
			return new Uploaded(MaterialUploadResponse.from(material), false);
		} finally {
			deleteQuietly(received);
		}
	}

	@Transactional
	public void delete(Long userId, Long id) {
		deleteAll(List.of(materialRepository.getOwned(id, userId)));
	}

	/**
	 * 과목을 지울 때 그 과목의 자료를 모두 지운다. 과목 삭제와 같은 트랜잭션에서만 부른다
	 * (과목 삭제가 롤백되면 자료와 파일도 그대로 남는다).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void deleteAllInCourse(Long userId, Long courseId) {
		deleteAll(materialRepository.findAllByUserIdAndCourseId(userId, courseId));
	}

	// AI 결과(퀴즈는 V6부터, 요약·개념은 나중에)는 material을 외래 키(ON DELETE CASCADE)로 가지므로 행과 함께 지워진다.
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

	// 요청 키는 선택이다. 비었으면 키 없이 올린 것으로 본다 (퀴즈 requestKey와 같은 64자 제한)
	private static String requestKey(String raw) {
		String key = raw == null ? "" : raw.strip();
		if (key.isEmpty()) {
			return null;
		}
		if (key.length() > Material.REQUEST_KEY_MAX_LENGTH) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, REQUEST_KEY_TOO_LONG_MESSAGE);
		}
		return key;
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
