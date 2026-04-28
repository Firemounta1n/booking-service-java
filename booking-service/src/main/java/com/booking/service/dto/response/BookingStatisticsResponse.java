package com.booking.service.dto.response;

import com.booking.service.entity.BookingStatus;

import java.util.List;
import java.util.Map;

/**
 * DTO агрегированной статистики по бронированиям.
 */
public record BookingStatisticsResponse(
        long totalBookings,
        Map<BookingStatus, Long> bookingsByStatus,
        List<TopResourceResponse> topResources) {
}
