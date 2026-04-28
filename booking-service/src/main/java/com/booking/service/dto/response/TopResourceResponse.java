package com.booking.service.dto.response;

/**
 * DTO количества бронирований по ресурсу.
 */
public record TopResourceResponse(
        Long resourceId,
        long bookingCount) {
}
