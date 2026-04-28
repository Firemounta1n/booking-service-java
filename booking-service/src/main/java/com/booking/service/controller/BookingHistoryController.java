package com.booking.service.controller;

import com.booking.service.dto.response.BookingStatusHistoryPageResponse;
import com.booking.service.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * REST контроллер для истории изменений бронирований.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingHistoryController {

    private final BookingService bookingService;

    /**
     * Получить историю изменений статуса бронирования.
     */
    @GetMapping("{id}/history")
    public BookingStatusHistoryPageResponse getStatusHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return bookingService.getStatusHistory(id, page, pageSize);
    }
}
