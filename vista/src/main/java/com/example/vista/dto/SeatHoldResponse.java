package com.example.vista.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatHoldResponse {
    private String holdId;
    private Long eventId;
    private List<Long> seatIds;
    private String status;
    private LocalDateTime holdExpiresAt;
}
