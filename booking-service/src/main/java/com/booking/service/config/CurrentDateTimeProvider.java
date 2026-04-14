package com.booking.service.config;

import java.time.OffsetDateTime;

/**
 * Интерфейс для абстракции текущего времени
 */
public interface CurrentDateTimeProvider {

    OffsetDateTime utcNow();

}
