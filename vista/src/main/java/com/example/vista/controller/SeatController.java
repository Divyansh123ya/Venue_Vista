package com.example.vista.controller;

import com.example.vista.dto.BookingRequestDto;
import com.example.vista.dto.SeatHoldRequest;
import com.example.vista.dto.SeatHoldResponse;
import com.example.vista.dto.SeatResponseDto;
import com.example.vista.entity.Booking;
import com.example.vista.entity.EventSeat;
import com.example.vista.repository.EventSeatRepository;
import com.example.vista.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/seats")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class SeatController {

    private final SeatService seatService;
    private final EventSeatRepository seatRepository;

    @GetMapping("/event/{eventId}")
    public List<EventSeat> getSeatsByEvent(@PathVariable Long eventId) {
        List<EventSeat> seats = seatRepository.findByEventId(eventId);
        if (seats == null || seats.isEmpty()) {
            seats = seatRepository.findAll();
        }

        // Detach event reference to prevent infinite recursion during JSON serialization
        for (EventSeat s : seats) {
            s.setEvent(null);
        }

        log.info("Total seats returned for event {}: {}", eventId, seats.size());
        return seats;
    }

    @PostMapping("/hold")
    public ResponseEntity<?> holdSeats(@RequestBody Map<String, Object> payload) {
        try {
            Long eventId = payload.get("eventId") != null
                    ? Long.valueOf(payload.get("eventId").toString())
                    : 1L;

            List<?> rawList = (List<?>) payload.get("seatIds");
            if (rawList == null || rawList.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No seats provided to hold"));
            }

            List<Long> seatIds = rawList.stream()
                    .map(item -> Long.valueOf(item.toString()))
                    .toList();

            String userId = payload.get("userId") != null
                    ? payload.get("userId").toString()
                    : "user_anon";

            SeatHoldRequest holdRequest = new SeatHoldRequest();
            holdRequest.setEventId(eventId);
            holdRequest.setSeatIds(seatIds);
            holdRequest.setUserId(userId);

            SeatHoldResponse holdResponse = seatService.holdSeats(holdRequest);

            Map<String, Object> response = new HashMap<>();
            response.put("holdReference", "HOLD-" + UUID.randomUUID().toString().substring(0, 8));
            response.put("expiresAt", holdResponse.getHoldExpiresAt() != null
                    ? holdResponse.getHoldExpiresAt().toString()
                    : LocalDateTime.now().plusMinutes(10).toString());
            response.put("seatIds", seatIds);
            response.put("message", "Seats locked successfully for 5 minutes");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during holdSeats: ", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/book")
    public ResponseEntity<?> bookDirectly(@RequestBody Map<String, Object> payload) {
        return handleBooking(payload);
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirmBooking(@RequestBody Map<String, Object> payload) {
        return handleBooking(payload);
    }

    private ResponseEntity<?> handleBooking(Map<String, Object> payload) {
        try {
            Long eventId = payload.get("eventId") != null
                    ? Long.valueOf(payload.get("eventId").toString())
                    : 1L;

            List<?> rawList = (List<?>) payload.get("seatIds");
            if (rawList == null || rawList.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No seat IDs provided"));
            }

            List<Long> seatIds = rawList.stream()
                    .map(item -> Long.valueOf(item.toString()))
                    .toList();

            BookingRequestDto bookingRequest = new BookingRequestDto();
            bookingRequest.setEventId(eventId);
            bookingRequest.setSeatIds(seatIds);
            bookingRequest.setCustomerName((String) payload.get("customerName"));
            bookingRequest.setCustomerEmail((String) payload.get("customerEmail"));
            bookingRequest.setCustomerPhone((String) payload.get("customerPhone"));
            bookingRequest.setPaymentMethod((String) payload.get("paymentMethod"));

            String bookingRef = "BKG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            Booking savedBooking = seatService.confirmBookingWithAttendee(bookingRequest, bookingRef);

            Map<String, Object> response = new HashMap<>();
            response.put("bookingReference", savedBooking.getBookingReference());
            response.put("status", "CONFIRMED");
            response.put("customerName", savedBooking.getCustomerName());
            response.put("customerEmail", savedBooking.getCustomerEmail());
            response.put("message", "Booking and attendee information saved successfully in MySQL!");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during booking execution: ", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}