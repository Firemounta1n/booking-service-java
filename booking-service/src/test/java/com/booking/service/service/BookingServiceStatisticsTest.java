package com.booking.service.service;

import com.booking.service.config.CurrentDateTimeProvider;
import com.booking.service.dto.response.TopResourceResponse;
import com.booking.service.entity.BookingStatus;
import com.booking.service.exception.BusinessException;
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
import org.springframework.data.domain.Pageable;

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
class BookingServiceStatisticsTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private BookingEventPublisher bookingEventPublisher;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

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
                outboxEventPublisher,
                dateTimeProvider
        );
    }

    @Test
    void getStatistics_returnsTotalAllStatusesAndTopResources() {
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo = LocalDate.of(2026, 1, 10);
        OffsetDateTime createdAtFrom = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime createdAtTo = OffsetDateTime.of(2026, 1, 11, 0, 0, 0, 0, ZoneOffset.UTC);

        when(bookingRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(createdAtFrom, createdAtTo))
                .thenReturn(8L);
        when(bookingRepository.countByStatus(createdAtFrom, createdAtTo))
                .thenReturn(List.of(
                        new StatusCount(BookingStatus.CONFIRMED, 3L),
                        new StatusCount(BookingStatus.CANCELLATION_PENDING, 2L)
                ));
        when(bookingRepository.findPopularResources(eq(createdAtFrom), eq(createdAtTo), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(
                        new ResourceCount(10L, 5L),
                        new ResourceCount(20L, 3L)
                ));

        var response = bookingService.getStatistics(dateFrom, dateTo);

        assertThat(response.totalBookings()).isEqualTo(8L);
        assertThat(response.byStatus())
                .containsOnlyKeys("awaitConfirmation", "confirmed", "cancellationPending", "cancelled")
                .containsEntry("awaitConfirmation", 0L)
                .containsEntry("confirmed", 3L)
                .containsEntry("cancellationPending", 2L)
                .containsEntry("cancelled", 0L);
        assertThat(response.byStatus()).doesNotContainKey("none");
        assertThat(response.topResources()).containsExactly(
                new TopResourceResponse(10L, 5L),
                new TopResourceResponse(20L, 3L)
        );
        assertThat(response.topResources().getFirst().bookingsCount()).isEqualTo(5L);

        verify(bookingRepository).countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(createdAtFrom, createdAtTo);
        verify(bookingRepository).countByStatus(createdAtFrom, createdAtTo);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingRepository).findPopularResources(eq(createdAtFrom), eq(createdAtTo), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void getStatistics_whenDateToBeforeDateFrom_throwsException() {
        LocalDate dateFrom = LocalDate.of(2026, 1, 10);
        LocalDate dateTo = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> bookingService.getStatistics(dateFrom, dateTo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dateTo");

        verifyNoInteractions(bookingRepository);
    }

    private record StatusCount(BookingStatus status, long bookingCount) implements BookingRepository.BookingStatusCount {

        @Override
        public BookingStatus getStatus() {
            return status;
        }

        @Override
        public long getBookingCount() {
            return bookingCount;
        }
    }

    private record ResourceCount(Long resourceId, long bookingCount) implements BookingRepository.ResourceBookingCount {

        @Override
        public Long getResourceId() {
            return resourceId;
        }

        @Override
        public long getBookingCount() {
            return bookingCount;
        }
    }
}
