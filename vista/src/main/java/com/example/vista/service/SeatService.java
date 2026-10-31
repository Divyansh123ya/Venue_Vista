package com.example.vista.service;

import com.example.vista.dto.*;
import com.example.vista.entity.Booking;
import com.example.vista.entity.EventSeat;
import com.example.vista.entity.SeatStatus;
import com.example.vista.repository.BookingRepository;
import com.example.vista.repository.EventSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatService {

    private final EventSeatRepository eventSeatRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final BookingRepository bookingRepository;
    @Transactional(readOnly = true)
    public List<SeatResponseDto> getSeatsForEvent(Long eventId) {
        return eventSeatRepository.findByEventId(eventId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public SeatHoldResponse holdSeats(SeatHoldRequest request) {
        String holdId = UUID.randomUUID().toString();
        // Hold seats for 10 minutes (matching UI countdown)
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        List<EventSeat> seats = eventSeatRepository.findAllById(request.getSeatIds());

        if (seats.isEmpty()) {
            throw new IllegalArgumentException("No seats found for the requested IDs.");
        }

        for (EventSeat seat : seats) {
            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                throw new IllegalStateException("Seat " + seat.getId() + " is already " + seat.getStatus());
            }
            seat.setStatus(SeatStatus.HELD);
            seat.setHoldExpiresAt(expiresAt);
        }

        eventSeatRepository.saveAll(seats);

        // Broadcast to all WebSocket listeners that these seats are now on hold
        broadcastSeatStatus(request.getEventId(), request.getSeatIds(), SeatStatus.HELD);

        log.info("Seats {} successfully placed on HOLD until {}", request.getSeatIds(), expiresAt);

        return SeatHoldResponse.builder()
                .holdId(holdId)
                .eventId(request.getEventId())
                .seatIds(request.getSeatIds())
                .status("HELD")
                .holdExpiresAt(expiresAt)
                .build();
    }

    @Transactional
    public List<SeatResponseDto> confirmBooking(BookingRequestDto request) {
        log.info("confirmBooking requested for seats: {}", request.getSeatIds());

        if (request.getSeatIds() == null || request.getSeatIds().isEmpty()) {
            throw new IllegalArgumentException("No seats provided in BookingRequestDto.");
        }

        List<EventSeat> seats = eventSeatRepository.findAllById(request.getSeatIds());

        if (seats.isEmpty()) {
            throw new IllegalArgumentException("No seats found in database for IDs: " + request.getSeatIds());
        }

        for (EventSeat seat : seats) {
            if (seat.getStatus() == SeatStatus.BOOKED) {
                throw new IllegalStateException("Seat " + seat.getRowLabel() + seat.getSeatNumber() + " is already booked.");
            }
            seat.setStatus(SeatStatus.BOOKED);
            seat.setHoldExpiresAt(null);
        }

        List<EventSeat> savedSeats = eventSeatRepository.saveAll(seats);

        // Safe broadcast with fallback eventId = 1L
        Long eventId = request.getEventId() != null ? request.getEventId() : 1L;
        try {
            broadcastSeatStatus(eventId, request.getSeatIds(), SeatStatus.BOOKED);
        } catch (Exception ex) {
            log.warn("WebSocket broadcast failed but DB update succeeded: {}", ex.getMessage());
        }

        log.info("Seats {} confirmed to BOOKED successfully in MySQL", request.getSeatIds());

        return savedSeats.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<SeatResponseDto> directBookSeats(BookingRequestDto request) {
        List<EventSeat> seats = eventSeatRepository.findAllById(request.getSeatIds());

        if (seats.isEmpty()) {
            throw new IllegalArgumentException("No seats found for direct booking.");
        }

        for (EventSeat seat : seats) {
            if (seat.getStatus() != SeatStatus.AVAILABLE && seat.getStatus() != SeatStatus.HELD) {
                throw new IllegalStateException("Seat " + seat.getId() + " is already booked.");
            }
            seat.setStatus(SeatStatus.BOOKED);
            seat.setHoldExpiresAt(null);
        }

        List<EventSeat> savedSeats = eventSeatRepository.saveAll(seats);
        broadcastSeatStatus(request.getEventId(), request.getSeatIds(), SeatStatus.BOOKED);

        log.info("Direct booking complete: seats {} marked as BOOKED", request.getSeatIds());

        return savedSeats.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private SeatResponseDto mapToDto(EventSeat seat) {
        return SeatResponseDto.builder()
                .id(seat.getId())
                .sectionName(seat.getSectionName())
                .rowLabel(seat.getRowLabel())
                .seatNumber(seat.getSeatNumber())
                .status(seat.getStatus())
                .price(seat.getPrice())
                .build();
    }

    public void broadcastSeatStatus(Long eventId, List<Long> seatIds, SeatStatus status) {
        SeatStatusUpdateDto update = new SeatStatusUpdateDto(eventId, seatIds, status);
        messagingTemplate.convertAndSend("/topic/events/" + eventId + "/seats", update);
    }

    @Transactional
    public Booking confirmBookingWithAttendee(BookingRequestDto request, String bookingRef) {
        log.info("Saving booking {} for attendee {}", bookingRef, request.getCustomerName());

        List<EventSeat> seats = eventSeatRepository.findAllById(request.getSeatIds());

        if (seats.isEmpty()) {
            throw new IllegalArgumentException("No seats found to confirm booking.");
        }

        BigDecimal calculatedTotal = BigDecimal.ZERO;

        for (EventSeat seat : seats) {
            if (seat.getStatus() == SeatStatus.BOOKED) {
                throw new IllegalStateException("Seat " + seat.getRowLabel() + seat.getSeatNumber() + " is already booked.");
            }
            seat.setStatus(SeatStatus.BOOKED);
            seat.setHoldExpiresAt(null);
            if (seat.getPrice() != null) {
                calculatedTotal = calculatedTotal.add(seat.getPrice());
            }
        }

        eventSeatRepository.saveAll(seats);

        // Save Attendee & Order record
        Booking booking = Booking.builder()
                .bookingReference(bookingRef)
                .eventId(request.getEventId() != null ? request.getEventId() : 1L)
                .customerName(request.getCustomerName() != null ? request.getCustomerName() : "Guest Attendee")
                .customerEmail(request.getCustomerEmail() != null ? request.getCustomerEmail() : "guest@vista.com")
                .customerPhone(request.getCustomerPhone())
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "UPI")
                .totalAmount(request.getTotalAmount() != null ? request.getTotalAmount() : calculatedTotal)
                .bookedAt(LocalDateTime.now())
                .seats(seats)
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        // Broadcast to WebSocket clients
        broadcastSeatStatus(request.getEventId() != null ? request.getEventId() : 1L, request.getSeatIds(), SeatStatus.BOOKED);

        return savedBooking;
    }
}