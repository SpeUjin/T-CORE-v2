package com.tcore.tcorev2.global.common;

import com.tcore.tcorev2.domain.entity.Concert;
import com.tcore.tcorev2.domain.entity.Schedule;
import com.tcore.tcorev2.domain.entity.Seat;
import com.tcore.tcorev2.domain.model.SeatStatus;
import com.tcore.tcorev2.domain.repository.ConcertRepository;
import com.tcore.tcorev2.domain.repository.ScheduleRepository;
import com.tcore.tcorev2.domain.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ConcertRepository concertRepository;
    private final ScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;

    @Override
    @Transactional // 하나의 트랜잭션으로 묶어 데이터 정합성을 보장합니다.
    public void run(String... args) {
        // 1. 데이터가 이미 있으면 중복 생성을 방지합니다.
        if (concertRepository.count() > 0) {
            return;
        }

        // 2. 공연 생성
        Concert concert = Concert.builder()
                .title("2026 IU HEREH WORLD TOUR")
                .artist("아이유(IU)")
                .description("아이유의 역대급 월드 투어 앙코르 콘서트")
                .build();
        concertRepository.save(concert);

        // 3. 공연 일정 생성 (오늘로부터 7일 뒤 저녁 7시)
        Schedule schedule = Schedule.builder()
                .concert(concert)
                .startTime(LocalDateTime.now().plusDays(7).withHour(19).withMinute(0))
                .build();
        scheduleRepository.save(schedule);

        // 4. 대량 좌석 생성 (A구역 1번 ~ 100번)
        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            Seat seat = Seat.builder()
                    .schedule(schedule)
                    .seatNumber("A" + i)
                    .price(150000)
                    .status(SeatStatus.AVAILABLE)
                    .build();
            seats.add(seat);
        }

        // saveAll을 사용하여 100개의 데이터를 한 번에 insert 합니다 (성능 최적화)
        seatRepository.saveAll(seats);

        System.out.println("✅ 초기 데이터 생성이 완료되었습니다: 공연 1개, 일정 1개, 좌석 100개");
    }
}