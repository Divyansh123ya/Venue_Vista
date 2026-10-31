package com.example.vista.controller;

import com.example.vista.dto.BookingRequestDto;
import com.example.vista.dto.SeatResponseDto;
import com.example.vista.service.SeatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final SeatService seatService;

    public BookingController(SeatService seatService) {
        this.seatService = seatService;
    }

    @PostMapping("/confirm")
    public ResponseEntity<List<SeatResponseDto>> confirmBooking(@RequestBody BookingRequestDto request) {
        List<SeatResponseDto> bookedSeats = seatService.confirmBooking(request);
        return ResponseEntity.ok(bookedSeats);
    }
}