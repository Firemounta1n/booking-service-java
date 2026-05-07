package com.booking.service.service;

import com.booking.service.config.CurrentDateTimeProvider;
import com.booking.service.entity.Booking;
import com.booking.service.entity.BookingStatus;
import com.booking.service.entity.BookingStatusHistory;
import com.booking.service.entity.ProcessedEvent;
import com.booking.service.messaging.listener.BookingEventPublisher;
import com.booking.service.repository.BookingRepository;
import com.booking.service.repository.BookingStatusHistoryRepository;
import com.booking.service.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceRaceConditionTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

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
                processedEventRepository,
                bookingEventPublisher,
                dateTimeProvider
        );
    }

    @Test
    void handleBookingJobConfirmed_whenCancellationPending_confirmsAndClearsCancellationFields() {
        UUID eventId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Booking booking = Booking.create(1L, 1L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 10), NOW);
        booking.setCatalogRequestId(requestId);
        booking.startCancellation(NOW.plusMinutes(1));

        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(bookingRepository.findByCatalogRequestId(requestId)).thenReturn(Optional.of(booking));
        when(dateTimeProvider.utcNow()).thenReturn(NOW.plusMinutes(2));

        bookingService.handleBookingJobConfirmed(eventId, requestId);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getPreviousStatus()).isNull();
        assertThat(booking.getCancellationSentAt()).isNull();
        verify(bookingRepository).save(booking);

        ArgumentCaptor<BookingStatusHistory> historyCaptor = ArgumentCaptor.forClass(BookingStatusHistory.class);
        verify(bookingStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getPreviousStatus()).isEqualTo(BookingStatus.CANCELLATION_PENDING);
        assertThat(historyCaptor.getValue().getNewStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(historyCaptor.getValue().getChangedAt()).isEqualTo(NOW.plusMinutes(2));
        assertThat(historyCaptor.getValue().getInitiator()).isEqualTo("System");

        ArgumentCaptor<ProcessedEvent> processedCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).saveAndFlush(processedCaptor.capture());
        assertThat(processedCaptor.getValue().getEventId()).isEqualTo(eventId);
    }

    @Test
    void handleBookingJobConfirmed_whenEventAlreadyProcessed_skipsProcessing() {
        UUID eventId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        bookingService.handleBookingJobConfirmed(eventId, requestId);

        verifyNoInteractions(bookingRepository);
        verifyNoInteractions(bookingStatusHistoryRepository);
        verify(processedEventRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void handleBookingJobDenied_whenEventAlreadyProcessed_skipsProcessing() {
        UUID eventId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        bookingService.handleBookingJobDenied(eventId, requestId);

        verifyNoInteractions(bookingRepository);
        verifyNoInteractions(bookingStatusHistoryRepository);
        verify(processedEventRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void handleError_whenEventAlreadyProcessed_skipsProcessing() {
        UUID eventId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        bookingService.handleError(eventId, requestId);

        verifyNoInteractions(bookingRepository);
        verifyNoInteractions(bookingStatusHistoryRepository);
        verify(processedEventRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void handleBookingJobConfirmed_whenConcurrentInsertCausesUniqueViolation_propagatesException() {
        UUID eventId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Booking booking = Booking.create(1L, 1L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 10), NOW);
        booking.setCatalogRequestId(requestId);

        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(bookingRepository.findByCatalogRequestId(requestId)).thenReturn(Optional.of(booking));
        when(dateTimeProvider.utcNow()).thenReturn(NOW.plusMinutes(1));
        when(processedEventRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(ProcessedEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> bookingService.handleBookingJobConfirmed(eventId, requestId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
