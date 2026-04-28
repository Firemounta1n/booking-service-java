package com.booking.service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * JPA Entity для истории изменений статуса бронирования.
 */
@Entity
@Table(name = "booking_status_history")
@Getter
@NoArgsConstructor
public class BookingStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private BookingStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private BookingStatus newStatus;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    @Column(nullable = false, length = 512)
    private String reason;

    @Column(nullable = false, length = 64)
    private String initiator;

    public static BookingStatusHistory create(Booking booking,
                                              BookingStatus previousStatus,
                                              BookingStatus newStatus,
                                              OffsetDateTime changedAt,
                                              String reason,
                                              String initiator) {
        BookingStatusHistory history = new BookingStatusHistory();
        history.bookingId = booking.getId();
        history.previousStatus = previousStatus;
        history.newStatus = newStatus;
        history.changedAt = changedAt;
        history.reason = reason;
        history.initiator = initiator;
        return history;
    }
}
