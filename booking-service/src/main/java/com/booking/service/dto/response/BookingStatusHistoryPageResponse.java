package com.booking.service.dto.response;

import java.util.List;

/**
 * DTO страницы истории изменения статуса бронирования.
 */
public record BookingStatusHistoryPageResponse(
        Long bookingId,
        long totalCount,
        List<BookingStatusHistoryResponse> items) {
}
