package com.booking.service.config;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

import static java.time.ZoneOffset.UTC;

/**
 * Реализация по умолчанию для получения текущего времени
 */
@Component
public class DefaultCurrentDateTimeProvider implements CurrentDateTimeProvider {

    @Override
    public OffsetDateTime utcNow() {
        return OffsetDateTime.now(UTC);
    }

}
