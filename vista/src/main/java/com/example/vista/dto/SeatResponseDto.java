package com.example.vista.dto;

import com.example.vista.entity.SeatStatus;
import lombok.*;

import java.math.BigDecimal;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatResponseDto {
    private Long id;
    private String sectionName;
    private String rowLabel;
    private String seatNumber;
    private SeatStatus status;
    private BigDecimal price;
}
