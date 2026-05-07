package com.booking.service.messaging.contracts;

import com.booking.service.entity.BookingStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Доменное событие изменения статуса бронирования.
 * Публикуется в RabbitMQ при подтверждении, отклонении и отмене бронирования.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingStatusChangedEvent {
    /**
     * Идентификатор события (для трассировки)
     */
    @JsonProperty("EventId")
    private UUID eventId;

    /**
     * Идентификатор бронирования
     */
    @JsonProperty("BookingId")
    private Long bookingId;

    /**
     * Предыдущий статус бронирования
     */
    @JsonProperty("PreviousStatus")
    private BookingStatus previousStatus;

    /**
     * Новый статус бронирования
     */
    @JsonProperty("NewStatus")
    private BookingStatus newStatus;

    /**
     * Время изменения статуса
     */
    @JsonProperty("ChangedAt")
    private OffsetDateTime changedAt;

    /**
     * Причина изменения статуса
     */
    @JsonProperty("Reason")
    private String reason;
}
