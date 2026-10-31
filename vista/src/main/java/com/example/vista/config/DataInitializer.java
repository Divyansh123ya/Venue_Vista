package com.example.vista.config;

import com.example.vista.entity.Event;
import com.example.vista.entity.EventSeat;
import com.example.vista.entity.SeatStatus;
import com.example.vista.entity.Venue;
import com.example.vista.repository.EventRepository;
import com.example.vista.repository.EventSeatRepository;
import com.example.vista.repository.VenueRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final EventSeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;

    public DataInitializer(EventSeatRepository seatRepository,
                           EventRepository eventRepository,
                           VenueRepository venueRepository) {
        this.seatRepository = seatRepository;
        this.eventRepository = eventRepository;
        this.venueRepository = venueRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        // 1. Wipe existing orphaned/old records to prevent constraint & duplicate errors
        seatRepository.deleteAll();

        // 2. Fetch or create a Venue
        Venue venue = venueRepository.findAll().stream().findFirst().orElseGet(() -> {
            Venue v = new Venue();
            v.setName("Jawaharlal Nehru Stadium");
            return venueRepository.save(v);
        });

        // 3. Fetch or create an Event linked to the Venue
        Event event = eventRepository.findAll().stream().findFirst().orElseGet(() -> {
            Event e = new Event();
            e.setTitle("Vista Live Concert");
            e.setVenue(venue);
            return eventRepository.save(e);
        });

        // Wipe existing seats first so clean S001-S036 numbers are assigned
        seatRepository.deleteAll();

        List<EventSeat> dummySeats = new ArrayList<>();
        String[] rows = {"A", "B", "C", "D", "E", "F"};
        int seatCounter = 1;

        for (String row : rows) {
            for (int i = 1; i <= 6; i++) {
                EventSeat seat = new EventSeat();
                seat.setEvent(event);
                seat.setRowLabel(row);
                seat.setSeatNumber(String.format("S%03d", seatCounter++)); // S001, S002, ..., S036
                seat.setStatus(SeatStatus.AVAILABLE);

                if ("A".equals(row)) {
                    seat.setSectionName("VVIP");
                    seat.setPrice(new BigDecimal("1299.00"));
                } else if ("B".equals(row) || "C".equals(row)) {
                    seat.setSectionName("VIP");
                    seat.setPrice(new BigDecimal("499.00"));
                } else {
                    seat.setSectionName("REGULAR");
                    seat.setPrice(new BigDecimal("299.00"));
                }

                dummySeats.add(seat);
            }
        }

        seatRepository.saveAll(dummySeats);
        System.out.println("✅ SUCCESSFULLY INSERTED 36 SEATS (S001 to S036)!");
    }
}