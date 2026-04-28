package com.booking.service.service;

import com.booking.service.config.CurrentDateTimeProvider;
import com.booking.service.dto.response.BookingStatusHistoryResponse;
import com.booking.service.entity.Booking;
import com.booking.service.entity.BookingStatus;
import com.booking.service.entity.BookingStatusHistory;
import com.booking.service.exception.BusinessException;
import com.booking.service.messaging.listener.BookingEventPublisher;
import com.booking.service.repository.BookingRepository;
import com.booking.service.repository.BookingStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceStatusHistoryTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;

    @Mock
    private BookingEventPublisher bookingEventPublisher;

    @Mock
    private CurrentDateTimeProvider dateTimeProvider;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                bookingRepository,
                bookingStatusHistoryRepository,
                bookingEventPublisher,
                dateTimeProvider
        );
    }

    @Test
    void getStatusHistory_requestsNewestEntriesFirst() {
        Long bookingId = 42L;
        Booking booking = Booking.create(1L, 1L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 10), NOW);
        BookingStatusHistory history = BookingStatusHistory.create(
                booking,
                BookingStatus.AWAIT_CONFIRMATION,
                BookingStatus.CONFIRMED,
                NOW.plusMinutes(1),
                "Catalog Service подтвердил бронирование",
                "System"
        );

        when(bookingRepository.existsById(bookingId)).thenReturn(true);
        when(bookingStatusHistoryRepository.findByBookingId(eq(bookingId), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(history)));

        var response = bookingService.getStatusHistory(bookingId, 1, 10);

        assertThat(response.bookingId()).isEqualTo(bookingId);
        assertThat(response.totalCount()).isEqualTo(1L);
        assertThat(response.items())
                .extracting(BookingStatusHistoryResponse::newStatus)
                .containsExactly(BookingStatus.CONFIRMED);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingStatusHistoryRepository).findByBookingId(eq(bookingId), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("changedAt"))
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void getStatusHistory_whenBookingDoesNotExist_throwsException() {
        Long bookingId = 42L;

        when(bookingRepository.existsById(bookingId)).thenReturn(false);

        assertThatThrownBy(() -> bookingService.getStatusHistory(bookingId, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("не найдено");

        verifyNoInteractions(bookingStatusHistoryRepository);
    }
}
