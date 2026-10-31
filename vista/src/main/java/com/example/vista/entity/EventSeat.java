package com.example.vista.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_seats", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"event_id", "section_name", "row_label", "seat_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    @JsonIgnore
    private Event event;

    @Column(name = "section_name", nullable = false)
    private String sectionName;

    @Column(name = "row_label", nullable = false)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    @Column(nullable = false)
    private BigDecimal price;
    @Version
    @Column(name="version")
    private Integer version;
    // Remove or comment out @Version until the column exists in MySQL
    // @Version
    // private Integer version;

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;
}