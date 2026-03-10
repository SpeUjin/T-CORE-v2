package com.tcore.tcorev2.application.service;

import com.tcore.tcorev2.domain.repository.ConcertRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ConcertServiceTest {

    @Mock
    private ConcertRepository concertRepository;

    @InjectMocks
    private ConcertService concertService;

    @Test
    @DisplayName("공연이 하나도 없을 때 빈 리스트를 반환하는지 확인")
    void getAllConcertsEmptyTest() {
        // given: Repository가 빈 리스트를 반환하도록 설정
        given(concertRepository.findAll()).willReturn(List.of());

        // when: 서비스 호출
        var result = concertService.getAllConcerts();

        // then: 결과 검증
        assertThat(result).isEmpty();
    }
}