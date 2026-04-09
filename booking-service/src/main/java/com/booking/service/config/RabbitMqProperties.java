package com.booking.service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация для интеграции с RabbitMQ через Rebus
 */
@Configuration
@ConfigurationProperties(prefix = "rabbitmq.rebus")
@Getter
@Setter
public class RabbitMqProperties {

    /**
     * Константы Rebus headers (протокол v2)
     */
    public static final String HEADER_MSG_TYPE = "rbs2-msg-type";
    public static final String HEADER_MSG_ID = "rbs2-msg-id";
    public static final String HEADER_CONTENT_TYPE = "rbs2-content-type";
    public static final String HEADER_INTENT = "rbs2-intent";
    public static final String HEADER_SENDER_ADDRESS = "rbs2-sender-address";

    /**
     * Адрес отправителя сообщений
     */
    private String senderAddress = "booking-service";

    /**
     * Тип контента для сообщений
     */
    private String contentType = "application/json;charset=utf-8";

    /**
     * Intent для публикации сообщений
     */
    private String intent = "pub";

    /**
     * Типы сообщений для Rebus (Assembly Qualified Names)
     */
    private MessageTypes messageTypes = new MessageTypes();

    @Getter
    @Setter
    public static class MessageTypes {
        /**
         * Команды (исходящие)
         */
        private String createBookingJob =
            "BookingService.Catalog.Async.Api.Contracts.Requests.CreateBookingJobRequest, BookingService.Catalog.Async.Api.Contracts";

        private String cancelBookingJob =
            "BookingService.Catalog.Async.Api.Contracts.Requests.CancelBookingJobByRequestIdRequest, BookingService.Catalog.Async.Api.Contracts";

        /**
         * События (входящие)
         */
        private String bookingJobConfirmed =
            "BookingService.Catalog.Async.Api.Contracts.Events.BookingJobConfirmed, BookingService.Catalog.Async.Api.Contracts";

        private String bookingJobDenied =
            "BookingService.Catalog.Async.Api.Contracts.Events.BookingJobDenied, BookingService.Catalog.Async.Api.Contracts";
    }
}
