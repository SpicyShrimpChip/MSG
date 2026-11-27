package com.msg.backend.domain.user.application;

import com.msg.backend.domain.auth.application.KakaoClient;
import com.msg.backend.domain.auth.application.LoginAttemptLimiter;
import com.msg.backend.domain.auth.application.PasswordPolicy;
import com.msg.backend.domain.auth.dao.UserSocialAccountRepository;
import com.msg.backend.domain.auth.entity.SocialProvider;
import com.msg.backend.domain.auth.entity.UserSocialAccount;
import com.msg.backend.domain.material.dao.MaterialRepository;
import com.msg.backend.domain.material.entity.Material;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.domain.user.entity.Role;
import com.msg.backend.domain.user.entity.User;
import com.msg.backend.global.error.BusinessException;
import com.msg.backend.global.llm.UserAiCallDailyRepository;
import com.msg.backend.global.storage.FileCleanupService;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴. 본인(토큰의 사용자)만, 지금 비밀번호를 다시 확인한 뒤 계정과 내 모든 학습 데이터를 지운다(hard delete).
 *
 * <p>DB: users 행을 지우면 FK ON DELETE CASCADE로 refresh_token, course(키워드), material, conversation(가져오기·조각·메시지),
 * analysis_run(모델 결과·판단·근거·합의), concept_feedback, quiz(문제·보기·제출·답·채점 기록), quiz_generation,
 * quiz_grading_run, quiz_ai_call, material_concept, concept_study_progress(개념 학습 기록, V12), user_social_account(카카오 연결)가 함께 지워진다.
 * 외래 키가 없는 user_ai_call_daily(하루 AI 호출 수, V4)는 여기서 직접 지운다. 공지사항(notice)은 작성자만 NULL이 되고 남는다(다른 사용자가 보는 글).
 * 파일: 자료 원본은 같은 트랜잭션에서 file_deletion_task로 남기고 커밋 뒤 지운다(실패하면 주기 작업이 다시 시도,
 * 자료 삭제와 같은 정책). 남은 Access Token은 사용자 행이 없으므로 다음 요청부터 401이다.
 *
 * <p>재가입 제한: 같은 트랜잭션에서 이메일의 키 해시와 재가입 가능 시각을 withdrawn_account에 남긴다(RejoinRestriction,
 * 키가 없으면 남기지 않음). 이 한 줄은 사용자와 연결되지 않고 기간이 지나면 지운다.
 *
 * <p>카카오: 카카오로만 가입해 비밀번호가 없는 계정은 비밀번호 대신 확인 문구 "탈퇴"를 받는다. 카카오 연결(user_social_account)이 있으면
 * withdraw가 끊을 회원번호를 돌려주고, 호출한 쪽(컨트롤러)이 탈퇴가 커밋된 뒤 unlinkKakao로 끊는다. 트랜잭션 콜백(afterCommit)에서
 * 부르면 커밋 뒤에도 DB 연결을 반납하기 전이라 카카오 응답을 기다리는 동안 연결 하나를 묶는다. 실패해도 탈퇴는 되돌리지 않는다.
 *
 * <p>비밀번호 확인은 로그인 시도 제한(LoginAttemptLimiter)과 같은 한도를 쓴다. 마지막 관리자는 탈퇴할 수 없다(관리자 없는 서비스 방지).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService {

	static final String WRONG_PASSWORD_MESSAGE = "비밀번호가 맞지 않아요. 다시 확인해 주세요.";
	static final String EMPTY_PASSWORD_MESSAGE = "비밀번호를 입력해 주세요.";
	static final String CONFIRM_TEXT = "탈퇴";
	static final String WRONG_CONFIRM_MESSAGE = "확인 문구로 \"탈퇴\"를 입력해 주세요.";
	static final String LAST_ADMIN_MESSAGE = "마지막 관리자는 탈퇴할 수 없어요. 다른 관리자를 먼저 지정해 주세요.";

	private final UserRepository userRepository;
	private final MaterialRepository materialRepository;
	private final FileCleanupService fileCleanupService;
	private final PasswordEncoder passwordEncoder;
	private final LoginAttemptLimiter attemptLimiter;
	private final RejoinRestriction rejoinRestriction;
	private final UserSocialAccountRepository socialAccountRepository;
	private final KakaoClient kakaoClient;
	private final UserAiCallDailyRepository aiCallDailyRepository;

	// 끊어야 할 카카오 회원번호를 돌려준다(없으면 빈 목록). 커밋된 뒤 unlinkKakao에 넘긴다
	@Transactional
	public List<String> withdraw(Long userId, String password, String confirmText, String clientIp) {
		// 사용자 행을 잠가 같은 사용자의 퀴즈 만들기·채점·동의 변경과 겹치지 않게 한다
		User user = userRepository.lockLoginUser(userId);
		if (user.hasPassword()) {
			checkPassword(user, password, clientIp);
		} else if (confirmText == null || !CONFIRM_TEXT.equals(confirmText.strip())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, WRONG_CONFIRM_MESSAGE);
		}
		if (user.getRole() == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
			throw new BusinessException(HttpStatus.CONFLICT, LAST_ADMIN_MESSAGE);
		}
		fileCleanupService.deleteAfterCommit(materialRepository.findAllByUserId(userId).stream()
				.map(Material::getStorageKey)
				.filter(Objects::nonNull)
				.toList());
		// 같은 이메일의 재가입 제한 기록 (이메일 원문이 아니라 키 해시, app.rejoin)
		rejoinRestriction.recordWithdrawal(user.getEmail());
		// 카카오 연결은 행이 지워지기 전에 읽어 두고, 탈퇴가 커밋된 뒤에 끊는다(카카오 정책)
		List<String> kakaoIds = kakaoIds(socialAccountRepository.findAllByUserId(userId));
		// 외래 키로 잇지 않은 사용자별 하루 AI 호출 수 (V4). 진행 중이던 모델 호출이 그 뒤에 남긴 줄은 주기 작업이 지운다
		aiCallDailyRepository.deleteAllOfUser(userId);
		userRepository.delete(user);
		userRepository.flush();
		return kakaoIds;
	}

	// 트랜잭션 밖에서 부른다. 실패해도 탈퇴는 이미 끝났으므로 기록만 남긴다(회원번호는 남기지 않는다).
	// 사용자는 카카오 계정 화면에서 직접 끊을 수 있다
	public void unlinkKakao(List<String> kakaoIds) {
		for (String kakaoId : kakaoIds) {
			try {
				kakaoClient.unlink(kakaoId);
			} catch (RuntimeException e) {
				log.warn("탈퇴한 사용자의 카카오 연결 끊기 실패 ({})", e.getClass().getSimpleName());
			}
		}
	}

	private void checkPassword(User user, String password, String clientIp) {
		// 입력 누락은 비밀번호 추측이 아니므로 시도 제한에 세지 않는다
		if (password == null || password.isEmpty()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, EMPTY_PASSWORD_MESSAGE);
		}
		attemptLimiter.checkAllowed(user.getEmail(), clientIp);
		if (PasswordPolicy.exceedsMaxBytes(password)
				|| !passwordEncoder.matches(password, user.getPasswordHash())) {
			attemptLimiter.recordFailure(user.getEmail(), clientIp);
			throw new BusinessException(HttpStatus.BAD_REQUEST, WRONG_PASSWORD_MESSAGE);
		}
		attemptLimiter.recordSuccess(user.getEmail(), clientIp);
	}

	private static List<String> kakaoIds(List<UserSocialAccount> accounts) {
		return accounts.stream()
				.filter(account -> account.getProvider() == SocialProvider.KAKAO)
				.map(UserSocialAccount::getProviderUserId)
				.toList();
	}
}
