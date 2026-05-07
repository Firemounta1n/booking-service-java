package com.booking.service.entity;

import com.booking.service.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingCancellationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);

    private Booking createBookingWithStatus(BookingStatus status) {
        Booking booking = Booking.create(1L, 1L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 10), NOW);
        if (status == BookingStatus.CONFIRMED) {
            booking.confirm();
        }
        return booking;
    }

    // === startCancellation ===

    @Test
    void startCancellation_fromAwaitConfirmation_success() {
        Booking booking = createBookingWithStatus(BookingStatus.AWAIT_CONFIRMATION);

        booking.startCancellation(NOW);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLATION_PENDING);
        assertThat(booking.getPreviousStatus()).isEqualTo(BookingStatus.AWAIT_CONFIRMATION);
        assertThat(booking.getCancellationSentAt()).isEqualTo(NOW);
    }

    @Test
    void startCancellation_fromConfirmed_success() {
        Booking booking = createBookingWithStatus(BookingStatus.CONFIRMED);

        booking.startCancellation(NOW);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLATION_PENDING);
        assertThat(booking.getPreviousStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getCancellationSentAt()).isEqualTo(NOW);
    }

    @Test
    void startCancellation_fromCancelled_throwsException() {
        Booking booking = createBookingWithStatus(BookingStatus.AWAIT_CONFIRMATION);
        booking.cancel(LocalDate.of(2026, 1, 15));

        assertThatThrownBy(() -> booking.startCancellation(NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Невозможно начать отмену из статуса");
    }

    // === completeCancellation ===

    @Test
    void completeCancellation_fromCancellationPending_success() {
        Booking booking = createBookingWithStatus(BookingStatus.CONFIRMED);
        booking.startCancellation(NOW);

        booking.completeCancellation();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getPreviousStatus()).isNull();
        assertThat(booking.getCancellationSentAt()).isNull();
    }

    // === confirm race condition ===

    @Test
    void confirm_fromCancellationPending_success() {
        Booking booking = createBookingWithStatus(BookingStatus.AWAIT_CONFIRMATION);
        booking.startCancellation(NOW);

        booking.confirm();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getPreviousStatus()).isNull();
        assertThat(booking.getCancellationSentAt()).isNull();
    }

    @Test
    void confirm_fromCancelled_throwsExceptionWithAllowedStatuses() {
        Booking booking = createBookingWithStatus(BookingStatus.AWAIT_CONFIRMATION);
        booking.cancel(LocalDate.of(2026, 1, 15));

        assertThatThrownBy(booking::confirm)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(BookingStatus.AWAIT_CONFIRMATION.name())
                .hasMessageContaining(BookingStatus.CANCELLATION_PENDING.name());
    }

    // === rollbackCancellation ===

    @Test
    void rollbackCancellation_restoresToAwaitConfirmation() {
        Booking booking = createBookingWithStatus(BookingStatus.AWAIT_CONFIRMATION);
        booking.startCancellation(NOW);

        booking.rollbackCancellation();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.AWAIT_CONFIRMATION);
        assertThat(booking.getPreviousStatus()).isNull();
        assertThat(booking.getCancellationSentAt()).isNull();
    }

    @Test
    void rollbackCancellation_restoresToConfirmed() {
        Booking booking = createBookingWithStatus(BookingStatus.CONFIRMED);
        booking.startCancellation(NOW);

        booking.rollbackCancellation();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getPreviousStatus()).isNull();
        assertThat(booking.getCancellationSentAt()).isNull();
    }

    @Test
    void rollbackCancellation_withoutPreviousStatus_throwsException() {
        Booking booking = createBookingWithStatus(BookingStatus.AWAIT_CONFIRMATION);

        assertThatThrownBy(() -> booking.rollbackCancellation())
                .isInstanceOf(BusinessException.class);
    }
}
