package com.example.vista.dto;
import lombok.*;
import java.util.List;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SeatHoldRequest {
    private Long eventId;
    private List<Long> seatIds;
    private String userId;
}
