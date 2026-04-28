package com.booking.service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Настройки повторной обработки зависших отмен.
 */
@Component
@ConfigurationProperties(prefix = "booking.cancellation")
@Getter
@Setter
public class CancellationRetryProperties {

    /**
     * Через какое время отмена считается зависшей.
     */
    private Duration retryTimeout = Duration.ofMinutes(5);
}
