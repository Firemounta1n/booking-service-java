package com.booking.service.service;

import com.booking.service.config.CancellationRetryProperties;
import com.booking.service.config.CurrentDateTimeProvider;
import com.booking.service.entity.Booking;
import com.booking.service.entity.BookingStatus;
import com.booking.service.messaging.contracts.CancelBookingJobByRequestIdRequest;
import com.booking.service.messaging.listener.BookingEventPublisher;
import com.booking.service.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaleCancellationRetryJobTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingEventPublisher bookingEventPublisher;

    @Mock
    private CurrentDateTimeProvider dateTimeProvider;

    private StaleCancellationRetryJob retryJob;
    private CancellationRetryProperties cancellationRetryProperties;

    @BeforeEach
    void setUp() {
        cancellationRetryProperties = new CancellationRetryProperties();
        cancellationRetryProperties.setRetryTimeout(Duration.ofMinutes(10));
        retryJob = new StaleCancellationRetryJob(
                bookingRepository,
                bookingEventPublisher,
                dateTimeProvider,
                cancellationRetryProperties
        );
    }

    @Test
    void retryStaleCancellations_whenOnePublishFails_continuesProcessingOthers() {
        UUID firstRequestId = UUID.randomUUID();
        UUID secondRequestId = UUID.randomUUID();
        Booking firstBooking = createPendingCancellation(firstRequestId);
        Booking secondBooking = createPendingCancellation(secondRequestId);
        OffsetDateTime sentBefore = NOW.minusMinutes(10);

        when(dateTimeProvider.utcNow()).thenReturn(NOW);
        when(bookingRepository.findStaleCancellations(BookingStatus.CANCELLATION_PENDING, sentBefore))
                .thenReturn(List.of(firstBooking, secondBooking));
        doThrow(new RuntimeException("RabbitMQ is unavailable"))
                .when(bookingEventPublisher)
                .publishCancelBookingJob(argThat(command -> firstRequestId.equals(command.getRequestId())));

        retryJob.retryStaleCancellations();

        ArgumentCaptor<CancelBookingJobByRequestIdRequest> commandCaptor =
                ArgumentCaptor.forClass(CancelBookingJobByRequestIdRequest.class);
        verify(bookingEventPublisher, times(2)).publishCancelBookingJob(commandCaptor.capture());
        assertThat(commandCaptor.getAllValues())
                .extracting(CancelBookingJobByRequestIdRequest::getRequestId)
                .containsExactly(firstRequestId, secondRequestId);

        verify(bookingRepository).findStaleCancellations(BookingStatus.CANCELLATION_PENDING, sentBefore);
        verifyNoMoreInteractions(bookingRepository);
    }

    private Booking createPendingCancellation(UUID requestId) {
        Booking booking = Booking.create(1L, 1L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 10), NOW);
        booking.setCatalogRequestId(requestId);
        booking.startCancellation(NOW.minusMinutes(15));
        return booking;
    }
}
