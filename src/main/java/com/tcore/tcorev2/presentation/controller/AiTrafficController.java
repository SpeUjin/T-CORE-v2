package com.tcore.tcorev2.presentation.controller;

import com.tcore.tcorev2.application.ai.TrafficControlAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai")
@RequiredArgsConstructor
public class AiTrafficController {

    private final TrafficControlAgent trafficControlAgent;

    /**
     * [Phase 6 테스트용 API]
     * 브라우저에서 이 주소를 호출하면 AI가 즉시 시스템 상태를 분석하고 조치를 취합니다.
     */
    @GetMapping("/check")
    public String checkTraffic(@RequestParam(defaultValue = "1") Long concertId) {
        log.info("🚨 관리자가 수동으로 AI 관제 시스템을 호출했습니다. (대상 콘서트: {})", concertId);

        // AI 에이전트에게 분석 및 제어 지시
        String aiReport = trafficControlAgent.analyzeAndControlTraffic(concertId);

        return "<h3>🤖 AIOps 관제 리포트</h3><p>" + aiReport + "</p>";
    }

    /**
     * [Phase 6 테스트용] 실제 CPU 부하(Spike) 강제 발생기 - 멀티 코어 버전
     */
    @GetMapping("/burn")
    public String burnCpu() {
        // 내 맥북의 코어 개수를 가져옵니다.
        int cores = Runtime.getRuntime().availableProcessors();
        log.warn("🔥 [Chaos Test] {}개의 모든 코어를 10초간 불태웁니다!", cores);

        // 코어 개수만큼 스레드 풀을 만듭니다.
        ExecutorService executor = Executors.newFixedThreadPool(cores);

        for (int i = 0; i < cores; i++) {
            executor.submit(() -> {
                long startTime = System.currentTimeMillis();
                // 각 스레드가 10초 동안 맹렬하게 수학 연산을 합니다.
                while (System.currentTimeMillis() - startTime < 10000) {
                    Math.sqrt(Math.random() * Math.random());
                }
            });
        }

        executor.shutdown(); // 스레드 풀 종료 예약

        return "🔥 " + cores + "개의 코어에 10초간 지옥불 부하를 발생시켰습니다. 빨리 AI 관제사를 호출하세요!";
    }
}