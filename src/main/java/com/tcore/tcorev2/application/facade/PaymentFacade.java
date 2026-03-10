package com.tcore.tcorev2.application.facade;

import com.tcore.tcorev2.application.service.PaymentService;
import com.tcore.tcorev2.application.service.RedisWaitingRoomService;
import com.tcore.tcorev2.infrastructure.payment.MockPgClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentService paymentService;
    private final MockPgClient mockPgClient;
    private final RedisWaitingRoomService waitingRoomService;

    /**
     * @Transactional 어노테이션이 없습니다! (DB 커넥션 점유 최소화)
     */
    public void executePayment(Long reservationId, Long userId, Long concertId, Long amount) {

        // 1. 가상 대기열 Active 상태 검증 (Phase 4 연동)
        if (!waitingRoomService.isUserActive(userId, concertId)) {
            throw new IllegalStateException("대기열 유효 시간이 만료되었거나 비정상적인 접근입니다.");
        }

        // 2. [DB 짧은 조회] 예약 정보 및 상태 검증 (PENDING 확인)
        paymentService.getValidPendingReservation(reservationId, userId);

        boolean paymentSuccess = false;
        try {
            // 3. [Network I/O] 외부 PG사 결제 요청 (DB 커넥션을 쥐고 있지 않음)
            paymentSuccess = mockPgClient.processPayment(userId, amount);

            // 4. [DB 트랜잭션] 결제 결과에 따른 상태 확정 또는 취소
            if (paymentSuccess) {
                paymentService.confirmPayment(reservationId);
                log.info("예매 확정 완료 - reservationId: {}", reservationId);
            } else {
                paymentService.cancelPayment(reservationId);
                log.warn("결제 실패로 인한 예매 취소 및 좌석 재개방 - reservationId: {}", reservationId);
                throw new IllegalStateException("PG사 결제에 실패했습니다.");
            }
        } catch (Exception e) {
            // 타임아웃 등 예기치 않은 오류 시 안전하게 취소(재개방) 처리
            if (!paymentSuccess) {
                paymentService.cancelPayment(reservationId);
            }
            throw e;
        } finally {
            // 5. 대기열 이탈 처리 (결제 성공이든 실패든 티켓팅 창을 벗어나므로 Active 상태 해제)
            waitingRoomService.removeActiveUser(userId, concertId);
        }
    }
}