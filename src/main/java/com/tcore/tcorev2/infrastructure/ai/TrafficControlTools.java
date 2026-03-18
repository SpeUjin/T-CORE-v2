package com.tcore.tcorev2.infrastructure.ai;

import com.tcore.tcorev2.application.service.RedisWaitingRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TrafficControlTools {

    private final RedisWaitingRoomService waitingRoomService;
    // AI가 우리 함수에 넘겨줄 파라미터 규격 (DTO)
    public record TrafficRequest(Long concertId, int allowedUsersPerSecond, String reason) {}

    /**
     * ⭐️ @Description은 매우 중요합니다!
     * LLM은 이 설명을 읽고 "아, 트래픽을 조절할 땐 이 함수를 써야겠구나"라고 스스로 판단합니다.
     */
    @Bean
    @Description("대기열에서 서비스로 진입하는 초당 허용 인원수(Rate Limit)를 동적으로 조절합니다.")
    public Function<TrafficRequest, String> adjustTrafficLimit() {
        return request -> {
            log.warn("[AIOps Tool Executed] 🚨 AI가 트래픽 제한을 변경했습니다!");
            log.warn("대상 콘서트: {}, 초당 허용 인원: {}명", request.concertId(), request.allowedUsersPerSecond());
            log.warn("AI의 변경 사유: {}", request.reason());

            // ⭐️ 진짜 핵심: AI의 결정을 실제 Redis 밸브에 적용합니다!
            waitingRoomService.setRateLimit(request.concertId(), request.allowedUsersPerSecond());

            return "트래픽 제한이 성공적으로 " + request.allowedUsersPerSecond() + "명으로 변경되었습니다.";
        };
    }
}