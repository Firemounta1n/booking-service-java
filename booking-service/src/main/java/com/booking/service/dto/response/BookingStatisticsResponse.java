package com.booking.service.dto.response;

import java.util.List;
import java.util.Map;

/**
 * DTO агрегированной статистики по бронированиям.
 */
public record BookingStatisticsResponse(
        long totalBookings,
        Map<String, Long> byStatus,
        List<TopResourceResponse> topResources) {
}
