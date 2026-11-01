package com.ailms.backend.global.error;

import com.ailms.backend.global.common.ApiResponse;
import com.ailms.backend.global.security.ApiErrorResponder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 오류를 { success: false, message, data: null }로 바꿔 돌려준다.
 * 예외 메시지·스택트레이스는 응답에 넣지 않고 서버 로그에만 남긴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	static final String INVALID_INPUT_MESSAGE = "입력한 내용을 확인해 주세요.";
	static final String UNREADABLE_BODY_MESSAGE = "요청 형식이 올바르지 않아요.";
	static final String NOT_FOUND_MESSAGE = "요청한 주소를 찾을 수 없어요.";
	static final String METHOD_NOT_ALLOWED_MESSAGE = "지원하지 않는 요청 방식이에요.";
	static final String INTERNAL_ERROR_MESSAGE = "서버에서 문제가 생겼어요. 잠시 뒤 다시 시도해 주세요.";
	static final String UNSUPPORTED_MEDIA_TYPE_MESSAGE = "지원하지 않는 요청 형식이에요.";
	static final String FILE_TOO_LARGE_MESSAGE = "파일이 너무 커요. 올릴 수 있는 크기를 확인해 주세요.";
	static final String MISSING_FILE_MESSAGE = "올릴 파일을 골라 주세요.";

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
		return respond(e.getStatus(), e.getMessage());
	}

	// @Valid 요청 본문·폼 검증 실패: DTO에 적은 검증 문구 중 첫 번째를 보여 준다
	@ExceptionHandler(BindException.class)
	public ResponseEntity<ApiResponse<Void>> handleBind(BindException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
				.map(FieldError::getDefaultMessage)
				.filter(text -> text != null && !text.isBlank())
				.findFirst()
				.orElse(INVALID_INPUT_MESSAGE);
		return respond(HttpStatus.BAD_REQUEST, message);
	}

	// 경로 변수·요청 파라미터 검증 실패, 타입이 맞지 않는 값, 빠진 파라미터
	@ExceptionHandler({
			HandlerMethodValidationException.class,
			MethodArgumentTypeMismatchException.class,
			MissingServletRequestParameterException.class
	})
	public ResponseEntity<ApiResponse<Void>> handleInvalidParameter(Exception e) {
		return respond(HttpStatus.BAD_REQUEST, INVALID_INPUT_MESSAGE);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
		return respond(HttpStatus.BAD_REQUEST, UNREADABLE_BODY_MESSAGE);
	}

	@ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
	public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception e) {
		return respond(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
		return respond(HttpStatus.METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED_MESSAGE);
	}

	// 예: 파일 올리기에 multipart가 아닌 JSON을 보냈을 때
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
		return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, UNSUPPORTED_MEDIA_TYPE_MESSAGE);
	}

	// 업로드 크기 한도(spring.servlet.multipart.*)를 넘었을 때. 서버가 파일을 다 받기 전에 끊는다
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
		return respond(HttpStatus.PAYLOAD_TOO_LARGE, FILE_TOO_LARGE_MESSAGE);
	}

	// multipart 요청에 파일 칸이 빠졌을 때
	@ExceptionHandler(MissingServletRequestPartException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException e) {
		return respond(HttpStatus.BAD_REQUEST, MISSING_FILE_MESSAGE);
	}

	// multipart 본문이 깨졌거나 multipart가 아닌 요청
	@ExceptionHandler(MultipartException.class)
	public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException e) {
		return respond(HttpStatus.BAD_REQUEST, UNREADABLE_BODY_MESSAGE);
	}

	// 컨트롤러 안에서 난 인증·권한 예외가 아래 500 처리로 가지 않게 한다 (필터 단계는 ApiErrorResponder가 처리)
	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException e) {
		return respond(HttpStatus.UNAUTHORIZED, ApiErrorResponder.UNAUTHORIZED_MESSAGE);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
		return respond(HttpStatus.FORBIDDEN, ApiErrorResponder.FORBIDDEN_MESSAGE);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
		log.error("처리하지 못한 예외", e);
		return respond(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE);
	}

	private ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(ApiResponse.error(message));
	}
}
