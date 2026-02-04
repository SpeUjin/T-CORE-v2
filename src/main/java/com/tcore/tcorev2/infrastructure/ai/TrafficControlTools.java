package com.tcore.tcorev2.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.tcore.tcorev2.application.port.SystemMetricPort;
import com.tcore.tcorev2.application.service.RedisWaitingRoomService;
import com.tcore.tcorev2.infrastructure.monitoring.MockSystemMetricAdapter; // Add this import
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class TrafficControlTools {

    private final SystemMetricPort systemMetricPort;
    private final RedisWaitingRoomService waitingRoomService;

    // --- 1. Metric Tool Definition ---

    // DTO for Input
    @JsonClassDescription("Request to retrieve current system metrics (CPU, Memory).")
    public record SystemLoadRequest(
            @JsonProperty(required = false, defaultValue = "default")
            @JsonPropertyDescription("Optional identifier for the metric source") String source
    ) {}

    // DTO for Output
    public record SystemLoadResponse(double cpuUsage, double memoryUsage) {}

    @Bean
    @Description("Get current system load (CPU and Memory usage). Useful for checking system health.")
    public Function<SystemLoadRequest, SystemLoadResponse> getSystemLoad() {
        return request -> {
            log.info("AI Agent called 'getSystemLoad'");
            return new SystemLoadResponse(
                    systemMetricPort.getCpuUsage(),
                    systemMetricPort.getMemoryUsage()
            );
        };
    }

    // --- 2. Queue Metric Tool Definition ---

    @JsonClassDescription("Request to retrieve waiting queue metrics for a specific concert.")
    public record QueueMetricsRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The ID of the concert") Long concertId
    ) {}

    public record QueueMetricsResponse(long waitingSize, long activeUserCount) {}

    @Bean
    @Description("Get waiting queue metrics. Returns the number of waiting users and active users.")
    public Function<QueueMetricsRequest, QueueMetricsResponse> getQueueMetrics() {
        return request -> {
            log.info("AI Agent called 'getQueueMetrics' for concertId: {}", request.concertId);
            return new QueueMetricsResponse(
                    systemMetricPort.getWaitingQueueSize(request.concertId),
                    systemMetricPort.getActiveUserCount(request.concertId)
            );
        };
    }

    // --- 3. Scaling Tool Definition ---

    @JsonClassDescription("Request to activate users from the waiting queue, allowing them to enter the reservation page.")
    public record ActivateUsersRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The ID of the concert") Long concertId,
            @JsonProperty(required = true)
            @JsonPropertyDescription("The number of users to activate") int count
    ) {}

    public record ActivateUsersResponse(boolean success, String message) {}

    @Bean
    @Description("Activate users from the waiting queue. Use this to allow more users to enter when system load is low.")
    public Function<ActivateUsersRequest, ActivateUsersResponse> activateUsers() {
        return request -> {
            log.info("AI Agent called 'activateUsers' for concertId: {}, count: {}", request.concertId, request.count);
            try {
                waitingRoomService.activateUsers(request.concertId, request.count);

                // MockSystemMetricAdapter의 상태도 업데이트 (Mocking 환경에서만 필요)
                if (systemMetricPort instanceof MockSystemMetricAdapter mockAdapter) {
                    long currentActive = mockAdapter.getActiveUserCount(request.concertId);
                    long currentWaiting = mockAdapter.getWaitingQueueSize(request.concertId);
                    mockAdapter.setActiveUserCount(request.concertId, currentActive + request.count);
                    mockAdapter.setWaitingQueueSize(request.concertId, Math.max(0, currentWaiting - request.count));
                }
                return new ActivateUsersResponse(true, "Successfully activated " + request.count + " users.");
            } catch (Exception e) {
                log.error("Failed to activate users", e);
                return new ActivateUsersResponse(false, "Failed: " + e.getMessage());
            }
        };
    }
}
