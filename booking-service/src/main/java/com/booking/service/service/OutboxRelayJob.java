package com.booking.service.service;

import com.booking.service.config.CurrentDateTimeProvider;
import com.booking.service.config.OutboxProperties;
import com.booking.service.entity.OutboxMessage;
import com.booking.service.messaging.contracts.BookingStatusChangedEvent;
import com.booking.service.messaging.listener.BookingEventPublisher;
import com.booking.service.repository.OutboxMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Фоновый процесс, который читает не отправленные outbox-сообщения и публикует их в RabbitMQ.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayJob {

    private final OutboxMessageRepository repository;
    private final BookingEventPublisher rabbitPublisher;
    private final ObjectMapper objectMapper;
    private final CurrentDateTimeProvider dateTimeProvider;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${booking.outbox.poll-interval}")
    @Transactional
    public void relay() {
        List<OutboxMessage> pending = repository.findPending(
                properties.getMaxAttempts(),
                PageRequest.of(0, properties.getBatchSize()));

        if (pending.isEmpty()) {
            return;
        }

        log.debug("Outbox: к публикации {} сообщений", pending.size());

        for (OutboxMessage message : pending) {
            publish(message);
        }
    }

    private void publish(OutboxMessage message) {
        try {
            BookingStatusChangedEvent event = objectMapper.readValue(
                    message.getPayload(), BookingStatusChangedEvent.class);

            rabbitPublisher.publishBookingStatusChanged(event);

            message.onPublished(dateTimeProvider.utcNow());
            log.info("Outbox-сообщение опубликовано: eventId={}", message.getEventId());
        } catch (Exception ex) {
            message.onFailure(ex.getMessage());
            log.error("Ошибка публикации outbox-сообщения: eventId={}, попыток={}/{}",
                    message.getEventId(), message.getAttempts(), properties.getMaxAttempts(), ex);
        }
    }
}
