package com.booking.service.messaging.listener;

import com.booking.service.config.RabbitMqProperties;
import com.booking.service.messaging.contracts.BookingStatusChangedEvent;
import com.booking.service.messaging.contracts.CancelBookingJobByRequestIdRequest;
import com.booking.service.messaging.contracts.CreateBookingJobRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.booking.service.config.RabbitMqProperties.*;

/**
 * Сервис для публикации сообщений в RabbitMQ
 * Использует Spring Cloud Stream для интеграции с Catalog Service (Rebus)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BookingEventPublisher {

    private final StreamBridge streamBridge;
    private final RabbitMqProperties rabbitMqProperties;

    /**
     * Публикует команду создания booking job в Catalog Service
     *
     * @param request команда с параметрами бронирования
     */
    public void publishCreateBookingJob(CreateBookingJobRequest request) {
        log.info("Публикация команды CreateBookingJob: requestId={}, resourceId={}, dates={} - {}",
                request.getRequestId(), request.getResourceId(),
                request.getStartDate(), request.getEndDate());

        Message<CreateBookingJobRequest> message = buildRebusMessage(
                request,
                rabbitMqProperties.getMessageTypes().getCreateBookingJob()
        );
        streamBridge.send("createBookingJob-out-0", message);

        log.info("Команда CreateBookingJob отправлена в RabbitMQ");
    }

    /**
     * Публикует команду отмены booking job в Catalog Service
     *
     * @param request команда с requestId для отмены
     */
    public void publishCancelBookingJob(CancelBookingJobByRequestIdRequest request) {
        log.info("Публикация команды CancelBookingJob: requestId={}", request.getRequestId());

        Message<CancelBookingJobByRequestIdRequest> message = buildRebusMessage(
                request,
                rabbitMqProperties.getMessageTypes().getCancelBookingJob()
        );
        streamBridge.send("cancelBookingJob-out-0", message);

        log.info("Команда CancelBookingJob отправлена в RabbitMQ");
    }

    /**
     * Публикует доменное событие изменения статуса бронирования
     *
     * @param event событие изменения статуса
     */
    public void publishBookingStatusChanged(BookingStatusChangedEvent event) {
        log.info("Публикация события BookingStatusChanged: bookingId={}, {} -> {}",
                event.getBookingId(), event.getPreviousStatus(), event.getNewStatus());

        Message<BookingStatusChangedEvent> message = buildRebusMessage(
                event,
                rabbitMqProperties.getMessageTypes().getBookingStatusChanged()
        );
        streamBridge.send("bookingStatusChanged-out-0", message);

        log.info("Событие BookingStatusChanged отправлено в RabbitMQ");
    }

    /**
     * Строит сообщение с обязательными Rebus headers
     *
     * @param payload     тело сообщения
     * @param messageType полный тип сообщения (assembly qualified name)
     * @return сообщение с Rebus headers
     */
    private <T> Message<T> buildRebusMessage(T payload, String messageType) {
        return MessageBuilder
                .withPayload(payload)
                .setHeader(HEADER_MSG_ID, UUID.randomUUID().toString())
                .setHeader(HEADER_MSG_TYPE, messageType)
                .setHeader(HEADER_CONTENT_TYPE, rabbitMqProperties.getContentType())
                .setHeader(HEADER_INTENT, rabbitMqProperties.getIntent())
                .setHeader(HEADER_SENDER_ADDRESS, rabbitMqProperties.getSenderAddress())
                .build();
    }
}
