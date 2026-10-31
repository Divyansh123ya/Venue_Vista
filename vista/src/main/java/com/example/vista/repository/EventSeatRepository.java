package com.example.vista.repository;

import com.example.vista.entity.EventSeat;
import jakarta.persistence.LockModeType;
import com.example.vista.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface EventSeatRepository extends JpaRepository<EventSeat, Long> {

    List<EventSeat> findByEventId(Long eventId);

    List<EventSeat> findByStatusAndHoldExpiresAtBefore(SeatStatus status, LocalDateTime now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT es FROM EventSeat es WHERE es.event.id = :eventId AND es.id IN :seatIds")
    List<EventSeat> findAllForUpdate(@Param("eventId") Long eventId, @Param("seatIds") List<Long> seatIds);
    @Modifying
    @Query("UPDATE EventSeat s SET s.status = :status, s.holdExpiresAt = null WHERE s.id IN :seatIds")
    int updateSeatStatus(@Param("seatIds") List<Long> seatIds, @Param("status") SeatStatus status);
}