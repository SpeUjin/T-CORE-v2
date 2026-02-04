package com.tcore.tcorev2.application.service;

import com.tcore.tcorev2.api.dto.ReservationRequest;
import com.tcore.tcorev2.application.facade.ReservationFacade;
import com.tcore.tcorev2.domain.entity.Concert;
import com.tcore.tcorev2.domain.entity.Schedule;
import com.tcore.tcorev2.domain.entity.Seat;
import com.tcore.tcorev2.domain.model.SeatStatus;
import com.tcore.tcorev2.domain.repository.ConcertRepository;
import com.tcore.tcorev2.domain.repository.ReservationRepository;
import com.tcore.tcorev2.domain.repository.SeatRepository;
import com.tcore.tcorev2.domain.repository.ScheduleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test") // 테스트용 설정 사용 (필요시)
class ReservationServiceConcurrencyTest {

    @Autowired
    private ReservationFacade reservationFacade;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private EntityManager entityManager;

    private Long targetSeatId;

    @BeforeEach
    void setUp() {
        // 1. 테스트용 콘서트 생성
        Concert concert = Concert.builder()
                .title("Test Concert")
                .artist("Test Artist")
                .build();
        Concert savedConcert = concertRepository.save(concert);

        // 2. 테스트용 스케줄 생성
        Schedule schedule = Schedule.builder()
                .concert(savedConcert)
                .startTime(LocalDateTime.now().plusDays(1))
                .build();
        scheduleRepository.save(schedule);

        // 3. 테스트용 좌석 생성 (AVAILABLE 상태)
        Seat seat = Seat.builder()
                .schedule(schedule)
                .seatNumber("A1")
                .price(10000)
                .status(SeatStatus.AVAILABLE)
                .build();
        Seat savedSeat = seatRepository.save(seat);
        targetSeatId = savedSeat.getId();
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
        scheduleRepository.deleteAll();
    }

    @Test
    @DisplayName("100명의 유저가 동시에 하나의 좌석을 예약하려 할 때, 단 1명만 성공해야 한다.")
    void reserveSeat_concurrency_test() throws InterruptedException {
        // given
        int numberOfThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < numberOfThreads; i++) {
            long userId = i + 1;
            executorService.submit(() -> {
                try {
                    ReservationRequest request = new ReservationRequest();
                    // 리플렉션으로 private 필드 설정 (DTO에 Setter가 없으므로)
                    ReflectionTestUtils.setField(request, "userId", userId);
                    ReflectionTestUtils.setField(request, "seatId", targetSeatId);

                    reservationFacade.reserveSeat(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 스레드 종료 대기

        // EntityManager의 캐시를 지워서 DB에서 최신 상태를 다시 조회하도록 강제
        entityManager.clear();

        // then
        Seat seat = seatRepository.findById(targetSeatId).orElseThrow();
        long reservationCount = reservationRepository.count();

        System.out.println("성공 횟수: " + successCount.get());
        System.out.println("실패 횟수: " + failCount.get());

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(numberOfThreads - 1);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);
        assertThat(reservationCount).isEqualTo(1);
    }
}
