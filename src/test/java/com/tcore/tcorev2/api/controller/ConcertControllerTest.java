package com.tcore.tcorev2.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ConcertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("공연 목록을 조회하면 200 OK와 JSON 배열이 반환된다")
    void getConcertsTest() throws Exception {
        mockMvc.perform(get("/api/v1/concerts"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray()); // 결과가 배열인지 확인
    }

    @Test
    @DisplayName("존재하지 않는 공연의 일정을 조회하면 빈 리스트가 반환된다")
    void getSchedulesEmptyTest() throws Exception {
        mockMvc.perform(get("/api/v1/concerts/999/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}