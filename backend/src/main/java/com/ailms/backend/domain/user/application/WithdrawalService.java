package com.ailms.backend.domain.user.application;

import com.ailms.backend.domain.auth.application.LoginAttemptLimiter;
import com.ailms.backend.domain.auth.application.PasswordPolicy;
import com.ailms.backend.domain.material.dao.MaterialRepository;
import com.ailms.backend.domain.material.entity.Material;
import com.ailms.backend.domain.user.dao.UserRepository;
import com.ailms.backend.domain.user.entity.Role;
import com.ailms.backend.domain.user.entity.User;
import com.ailms.backend.global.error.BusinessException;
import com.ailms.backend.global.security.ApiErrorResponder;
import com.ailms.backend.global.storage.FileCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴. 본인(토큰의 사용자)만, 지금 비밀번호를 다시 확인한 뒤 계정과 내 모든 학습 데이터를 지운다(hard delete).
 *
 * <p>DB: users 행을 지우면 FK ON DELETE CASCADE로 refresh_token, course(키워드), material, conversation(가져오기·조각·메시지),
 * analysis_run(모델 결과·판단·근거·합의), concept_feedback, quiz(문제·보기·제출·답·채점 기록), quiz_generation,
 * quiz_grading_run, quiz_ai_call, material_concept, concept_study_progress(개념 학습 기록, V12)가 함께 지워진다. 공지사항(notice)은 작성자만 NULL이 되고 남는다(다른 사용자가 보는 글).
 * 파일: 자료 원본은 같은 트랜잭션에서 file_deletion_task로 남기고 커밋 뒤 지운다(실패하면 주기 작업이 다시 시도,
 * 자료 삭제와 같은 정책). 남은 Access Token은 사용자 행이 없으므로 다음 요청부터 401이다.
 *
 * <p>비밀번호 확인은 로그인 시도 제한(LoginAttemptLimiter)과 같은 한도를 쓴다. 마지막 관리자는 탈퇴할 수 없다(관리자 없는 서비스 방지).
 */
@Service
@RequiredArgsConstructor
public class WithdrawalService {

	static final String WRONG_PASSWORD_MESSAGE = "비밀번호가 맞지 않아요. 다시 확인해 주세요.";
	static final String LAST_ADMIN_MESSAGE = "마지막 관리자는 탈퇴할 수 없어요. 다른 관리자를 먼저 지정해 주세요.";

	private final UserRepository userRepository;
	private final MaterialRepository materialRepository;
	private final FileCleanupService fileCleanupService;
	private final PasswordEncoder passwordEncoder;
	private final LoginAttemptLimiter attemptLimiter;

	@Transactional
	public void withdraw(Long userId, String password, String clientIp) {
		// 사용자 행을 잠가 같은 사용자의 퀴즈 만들기·채점·동의 변경과 겹치지 않게 한다
		User user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, ApiErrorResponder.UNAUTHORIZED_MESSAGE));
		attemptLimiter.checkAllowed(user.getEmail(), clientIp);
		if (PasswordPolicy.exceedsMaxBytes(password) || !passwordEncoder.matches(password, user.getPasswordHash())) {
			attemptLimiter.recordFailure(user.getEmail(), clientIp);
			throw new BusinessException(HttpStatus.BAD_REQUEST, WRONG_PASSWORD_MESSAGE);
		}
		attemptLimiter.recordSuccess(user.getEmail(), clientIp);
		if (user.getRole() == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
			throw new BusinessException(HttpStatus.CONFLICT, LAST_ADMIN_MESSAGE);
		}
		fileCleanupService.deleteAfterCommit(materialRepository.findAllByUserId(userId).stream()
				.map(Material::getStorageKey)
				.toList());
		userRepository.delete(user);
		userRepository.flush();
	}
}
