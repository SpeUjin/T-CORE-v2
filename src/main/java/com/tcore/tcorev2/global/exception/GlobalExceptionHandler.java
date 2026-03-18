package com.tcore.tcorev2.global.exception;

import com.tcore.tcorev2.global.common.CommonResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 프로젝트 전역에서 발생하는 예외를 낚아채서(Intercept),
 * 클라이언트(프론트엔드)가 이해하기 쉬운 HTTP 상태 코드와 공통 포맷으로 변환해 주는 클래스입니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * [Phase 5: 동시성 예외 처리]
     */
    @ExceptionHandler(IllegalStateException.class)
    // 🚨 기존 <String> 을 <Void> 로 변경합니다! (와일드카드인 <?> 로 하셔도 무방합니다)
    public ResponseEntity<CommonResponse<Void>> handleIllegalStateException(IllegalStateException e) {

        log.warn("[Exception] IllegalStateException 발생: {}", e.getMessage());

        if (e.getMessage() != null && e.getMessage().contains("이미 선택되었거나 판매된 좌석")) {
            return ResponseEntity.status(HttpStatus.CONFLICT) // 409
                    .body(CommonResponse.error("이미 누군가 선점하여 예매가 완료된 좌석입니다."));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST) // 400
                .body(CommonResponse.error(e.getMessage()));
    }

    /**
     * (보너스) 앞으로 다른 예외들이 생기면 여기에 메서드를 계속 추가하시면 됩니다!
     * 예: @ExceptionHandler(IllegalArgumentException.class)
     */
}