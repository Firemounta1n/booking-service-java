package com.booking.service.dto.response;

import com.booking.service.entity.BookingStatus;

import java.time.OffsetDateTime;

/**
 * DTO записи истории изменения статуса бронирования.
 */
public record BookingStatusHistoryResponse(
        Long id,
        BookingStatus previousStatus,
        BookingStatus newStatus,
        OffsetDateTime changedAt,
        String reason,
        String initiator) {
}
