package com.example.vista;

import com.example.vista.dto.SeatHoldRequest;
import com.example.vista.repository.EventSeatRepository;
import com.example.vista.service.SeatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class SeatConcurrencyTest {

    @Autowired
    private SeatService seatService;

    @Autowired
    private EventSeatRepository eventSeatRepository;

    @Test
    public void testConcurrentSeatHold_OptimisticLocking() throws InterruptedException {
        Long targetSeatId = 1L;

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Thread 1
        executorService.submit(() -> {
            try {
                latch.await();
                SeatHoldRequest req1 = new SeatHoldRequest();
                req1.setEventId(1L);
                req1.setSeatIds(List.of(targetSeatId));
                req1.setUserId("User_A");

                seatService.holdSeats(req1);
                successCount.incrementAndGet();
            } catch (OptimisticLockingFailureException | IllegalStateException e) {
                failureCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        });

        // Thread 2
        executorService.submit(() -> {
            try {
                latch.await();
                SeatHoldRequest req2 = new SeatHoldRequest();
                req2.setEventId(1L);
                req2.setSeatIds(List.of(targetSeatId));
                req2.setUserId("User_B");

                seatService.holdSeats(req2);
                successCount.incrementAndGet();
            } catch (OptimisticLockingFailureException | IllegalStateException e) {
                failureCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        });

        latch.countDown(); // Trigger both threads at once

        executorService.shutdown();
        while (!executorService.isTerminated()) {
            Thread.sleep(50);
        }

        System.out.println("Successful Holds: " + successCount.get());
        System.out.println("Failed Holds (Lock/State collision): " + failureCount.get());

        assertEquals(1, successCount.get());
        assertEquals(1, failureCount.get());
    }
}