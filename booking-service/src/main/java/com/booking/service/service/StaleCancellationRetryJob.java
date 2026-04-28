package com.booking.service.service;

import com.booking.service.config.CancellationRetryProperties;
import com.booking.service.config.CurrentDateTimeProvider;
import com.booking.service.entity.Booking;
import com.booking.service.entity.BookingStatus;
import com.booking.service.messaging.contracts.CancelBookingJobByRequestIdRequest;
import com.booking.service.messaging.listener.BookingEventPublisher;
import com.booking.service.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Повторно отправляет команды отмены для зависших CANCELLATION_PENDING бронирований.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StaleCancellationRetryJob {

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher bookingEventPublisher;
    private final CurrentDateTimeProvider dateTimeProvider;
    private final CancellationRetryProperties cancellationRetryProperties;

    @Scheduled(fixedDelayString = "${booking.cancellation.retry-interval}")
    @Transactional(readOnly = true)
    public void retryStaleCancellations() {
        OffsetDateTime sentBefore = dateTimeProvider.utcNow()
                .minus(cancellationRetryProperties.getRetryTimeout());

        List<Booking> staleCancellations = bookingRepository.findStaleCancellations(
                BookingStatus.CANCELLATION_PENDING, sentBefore);

        if (staleCancellations.isEmpty()) {
            return;
        }

        log.warn("Найдено зависших отмен бронирований: {}", staleCancellations.size());

        for (Booking booking : staleCancellations) {
            retryCancellation(booking);
        }
    }

    private void retryCancellation(Booking booking) {
        try {
            CancelBookingJobByRequestIdRequest command = new CancelBookingJobByRequestIdRequest(
                    UUID.randomUUID(),
                    booking.getCatalogRequestId()
            );

            bookingEventPublisher.publishCancelBookingJob(command);
            log.info("Повторно отправлена команда отмены бронирования: id={}, requestId={}",
                    booking.getId(), booking.getCatalogRequestId());
        } catch (Exception ex) {
            log.error("Ошибка повторной отправки команды отмены бронирования: id={}, requestId={}",
                    booking.getId(), booking.getCatalogRequestId(), ex);
        }
    }
}
