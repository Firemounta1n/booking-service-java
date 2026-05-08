package com.booking.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Outbox-запись с доменным событием для отложенной публикации в брокер сообщений.
 */
@Entity
@Table(name = "outbox_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 256, updatable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    private OutboxMessage(UUID eventId, String eventType, String payload, OffsetDateTime createdAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
        this.attempts = 0;
    }

    public static OutboxMessage forEvent(UUID eventId,
                                         String eventType,
                                         String payload,
                                         OffsetDateTime createdAt) {
        return new OutboxMessage(eventId, eventType, payload, createdAt);
    }

    public void onPublished(OffsetDateTime publishedAt) {
        this.processedAt = publishedAt;
        this.lastError = null;
    }

    public void onFailure(String error) {
        this.attempts++;
        this.lastError = clip(error);
    }

    private static String clip(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1024 ? value : value.substring(0, 1024);
    }
}
