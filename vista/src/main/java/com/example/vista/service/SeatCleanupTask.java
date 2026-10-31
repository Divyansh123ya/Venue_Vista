package com.example.vista.service;

import com.example.vista.entity.EventSeat;
import com.example.vista.entity.SeatStatus;
import com.example.vista.repository.EventSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeatCleanupTask {

    private final EventSeatRepository eventSeatRepository;
    private final SeatService seatService;
    // Har 60 second (1 minute) mein yeh task chalega
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void releaseExpiredSeats() {
        LocalDateTime now = LocalDateTime.now();
        List<EventSeat> expiredSeats = eventSeatRepository.findByStatusAndHoldExpiresAtBefore(SeatStatus.HELD, now);

        if (!expiredSeats.isEmpty()) {
            for (EventSeat seat : expiredSeats) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seatService.broadcastSeatStatus(seat.getEvent().getId(), List.of(seat.getId()), SeatStatus.AVAILABLE);
                seat.setHoldExpiresAt(null); // Clear expiration timestamp
            }
            eventSeatRepository.saveAll(expiredSeats);
            log.info("Released {} expired seats back to AVAILABLE status.", expiredSeats.size());
        }
    }
}