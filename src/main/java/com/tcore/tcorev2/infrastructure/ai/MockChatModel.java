package com.tcore.tcorev2.infrastructure.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.ModelOptions;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

/**
 * 비용 절감 및 테스트를 위한 Mock ChatModel 구현체.
 * 실제 LLM을 호출하지 않고, 정해진 규칙에 따라 더미 응답을 반환합니다.
 */
@Component
@Profile({"dev", "test"})
public class MockChatModel implements ChatModel {

    @Override
    public ChatResponse call(Prompt prompt) {
        String userMessage = prompt.getContents();
        String responseContent;

        // 간단한 키워드 기반 응답 로직 (시뮬레이션)
        if (userMessage.contains("system status")) {
            // 시스템 상태를 물어보면 Tool Call을 유도하는 응답 (실제로는 Tool Call JSON 구조를 반환해야 함)
            // 여기서는 단순화하여 텍스트 응답으로 처리하거나, 
            // 실제 Spring AI의 Function Call 매커니즘을 테스트하려면 복잡한 Mocking이 필요하므로
            // 우선은 텍스트 응답으로 "LOGIC: CHECK_METRICS" 같은 신호를 보내는 방식으로 우회할 수도 있습니다.
            // 하지만 Spring AI의 구조상 ChatModel이 직접 Tool을 실행하는게 아니라,
            // LLM이 "함수 실행해줘"라는 JSON을 뱉으면 Spring AI 프레임워크가 그걸 보고 실행하는 구조입니다.
            
            responseContent = "I will check the system status.";
        } else if (userMessage.contains("high load")) {
            responseContent = "System load is high. I recommend reducing the admission rate.";
        } else {
            responseContent = "I am a Mock AI Agent. System seems stable.";
        }

        Generation generation = new Generation(new AssistantMessage(responseContent));
        return new ChatResponse(Collections.singletonList(generation));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ChatOptions.builder().build();
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }

    // Spring AI 최신 버전 호환성 메서드 (필요 시 구현)
//    public ChatResponse call(String message) {
//        return call(new Prompt(message));
//    }
}
