package com.booking.service.service;

import com.booking.service.config.CurrentDateTimeProvider;
import com.booking.service.entity.OutboxMessage;
import com.booking.service.messaging.contracts.BookingStatusChangedEvent;
import com.booking.service.repository.OutboxMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Сохраняет доменные события в outbox в рамках транзакции вызывающего кода.
 * Атомарность INSERT в outbox_messages с изменением агрегата обеспечивается
 * общим Spring transaction context — отдельная транзакция здесь недопустима.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private final OutboxMessageRepository repository;
    private final ObjectMapper objectMapper;
    private final CurrentDateTimeProvider dateTimeProvider;

    public void publishBookingStatusChanged(BookingStatusChangedEvent event) {
        OutboxMessage message = OutboxMessage.forEvent(
                event.getEventId(),
                BookingStatusChangedEvent.class.getName(),
                serialize(event),
                dateTimeProvider.utcNow()
        );
        repository.save(message);

        log.info("Записано outbox-сообщение: eventId={}, bookingId={}, {} -> {}",
                event.getEventId(), event.getBookingId(),
                event.getPreviousStatus(), event.getNewStatus());
    }

    private String serialize(BookingStatusChangedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Не удалось сериализовать BookingStatusChangedEvent: " + event.getEventId(), ex);
        }
    }
}
