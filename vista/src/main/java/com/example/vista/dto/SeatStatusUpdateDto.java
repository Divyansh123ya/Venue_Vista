package com.example.vista.dto;

import com.example.vista.entity.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatStatusUpdateDto {
    private Long eventId;
    private List<Long> seatIds;
    private SeatStatus status; // AVAILABLE, HOLD, BOOKED
}