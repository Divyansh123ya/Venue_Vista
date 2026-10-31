package com.example.vista.dto;

import java.util.List;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class BookingRequestDto {
    private Long eventId;
    private List<Long> seatIds;
    private String userId;
    private String paymentReference;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String paymentMethod;
    private BigDecimal totalAmount;

    // Getters and Setters
    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public List<Long> getSeatIds() { return seatIds; }
    public void setSeatIds(List<Long> seatIds) { this.seatIds = seatIds; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
}