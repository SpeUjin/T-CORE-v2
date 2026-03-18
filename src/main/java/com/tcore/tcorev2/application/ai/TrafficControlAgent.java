package com.tcore.tcorev2.application.ai;

import com.tcore.tcorev2.application.port.SystemMetricPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TrafficControlAgent {

    private final ChatClient chatClient;
    private final SystemMetricPort systemMetricPort;

    // ChatClient.Builder를 통해 Spring AI 채팅 클라이언트를 생성합니다.
    public TrafficControlAgent(ChatClient.Builder chatClientBuilder, SystemMetricPort systemMetricPort) {
        this.chatClient = chatClientBuilder.build();
        this.systemMetricPort = systemMetricPort;
    }

    /**
     * 시스템 상태를 수집하여 AI에게 분석을 요청합니다.
     */
    public String analyzeAndControlTraffic(Long concertId) {
        // 1. 현재 시스템 지표 수집 (우리가 방금 만든 눈!)
        double cpuUsage = systemMetricPort.getCpuUsage();
        int dbConnections = systemMetricPort.getActiveDbConnections();
        long queueSize = systemMetricPort.getWaitingQueueSize(concertId);

        log.info("[AIOps] 지표 수집 완료 - CPU: {}%, DB 커넥션: {}, 대기열: {}", cpuUsage, dbConnections, queueSize);

        // 2. AI에게 부여할 페르소나와 규칙 (System Prompt)
        String systemPrompt = """
            당신은 대규모 티켓팅 서비스 'T-CORE'의 인프라를 보호하는 수석 AIOps 엔지니어입니다.
            현재 시스템의 지표(CPU 사용량, 활성 DB 커넥션 수, 대기열 인원)를 분석하여 시스템이 다운되지 않도록 방어하세요.
            
            [판단 기준 및 행동 지침]
            1. 'CPU 70% 이상' 또는 'DB 커넥션 80개 이상'일 경우: 심각한 부하 상태입니다. 
               반드시 `adjustTrafficLimit` 도구를 호출하여 초당 입장 허용량을 10명으로 줄이세요.
            2. 'CPU 40% 이하' 및 'DB 커넥션 30개 이하'일 경우: 안정적인 상태입니다.
               `adjustTrafficLimit` 도구를 호출하여 초당 입장 허용량을 100명으로 늘려서 회전율을 높이세요.
            3. 그 외의 경우: 현재 상태를 유지하며 모니터링을 계속한다는 분석 결과를 작성하세요.
            
            분석 결과는 한국어로 간결하게 2~3문장으로 요약해서 답변하세요.
            """;

        // 3. AI에게 전달할 현재 상태 (User Prompt)
        String userMessage = String.format(
                "현재 상태 보고 - 대상 콘서트 ID: %d, CPU: %.1f%%, DB 커넥션: %d개, 대기열 인원: %d명",
                concertId, cpuUsage, dbConnections, queueSize
        );

        // 4. Spring AI 호출 및 Function Calling 연결
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                // ⭐️ 핵심: AI가 판단 후 아래 이름의 Bean(도구)을 자율적으로 실행하도록 권한을 줍니다.
                .functions("adjustTrafficLimit")
                .call()
                .content();
    }

    /**
     * [자율 관제 모드]
     * 10초마다 AI 관제사가 스스로 인프라 지표를 확인하고 트래픽을 조절합니다.
     */
    @Scheduled(fixedRate = 10000) // 10초(10000ms)마다 무한 반복
    public void autonomousPatrol() {
        Long targetConcertId = 1L; // 현재 모니터링할 타겟 콘서트 ID

        log.info("[AIOps] 🤖 자율 관제 에이전트 순찰 중... (대상 콘서트: {})", targetConcertId);

        try {
            // 기존에 컨트롤러가 수동으로 호출하던 그 메서드를 스케줄러가 알아서 호출합니다!
            this.analyzeAndControlTraffic(targetConcertId);
        } catch (Exception e) {
            log.error("[AIOps] 자율 관제 중 오류 발생", e);
        }
    }
}