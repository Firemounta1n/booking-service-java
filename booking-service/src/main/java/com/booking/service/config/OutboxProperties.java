package com.booking.service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Параметры relay-цикла для Transactional Outbox Pattern.
 */
@Component
@ConfigurationProperties(prefix = "booking.outbox")
@Getter
@Setter
public class OutboxProperties {

    /**
     * Период между запусками relay-job.
     */
    private Duration pollInterval = Duration.ofSeconds(5);

    /**
     * Сколько попыток публикации делается до того, как сообщение замораживается.
     */
    private int maxAttempts = 5;

    /**
     * Сколько сообщений relay забирает за одну итерацию.
     */
    private int batchSize = 50;
}
