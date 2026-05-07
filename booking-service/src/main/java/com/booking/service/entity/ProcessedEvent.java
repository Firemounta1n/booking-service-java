package com.booking.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA Entity для хранения идентификаторов уже обработанных событий.
 * Обеспечивает идемпотентность обработки сообщений из RabbitMQ
 * (at-least-once delivery — возможны повторы и параллельные доставки).
 */
@Entity
@Table(name = "processed_events")
@Getter
@NoArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    public static ProcessedEvent create(UUID eventId, String eventType, OffsetDateTime processedAt) {
        ProcessedEvent processedEvent = new ProcessedEvent();
        processedEvent.eventId = eventId;
        processedEvent.eventType = eventType;
        processedEvent.processedAt = processedAt;
        return processedEvent;
    }
}
