package com.booking.service.service;

import com.booking.service.config.CurrentDateTimeProvider;
import com.booking.service.dto.response.BookingStatisticsResponse;
import com.booking.service.dto.response.BookingStatusHistoryPageResponse;
import com.booking.service.dto.response.BookingStatusHistoryResponse;
import com.booking.service.dto.response.TopResourceResponse;
import com.booking.service.entity.Booking;
import com.booking.service.entity.BookingStatus;
import com.booking.service.entity.BookingStatusHistory;
import com.booking.service.entity.ProcessedEvent;
import com.booking.service.exception.BusinessException;
import com.booking.service.messaging.contracts.BookingStatusChangedEvent;
import com.booking.service.messaging.contracts.CancelBookingJobByRequestIdRequest;
import com.booking.service.messaging.contracts.CreateBookingJobRequest;
import com.booking.service.messaging.listener.BookingEventPublisher;
import com.booking.service.repository.BookingRepository;
import com.booking.service.repository.BookingStatusHistoryRepository;
import com.booking.service.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис для работы с бронированиями
 * Объединяет CRUD операции, бизнес-логику и обработку событий
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private static final List<BookingStatus> STATISTICS_STATUSES = List.of(
            BookingStatus.AWAIT_CONFIRMATION,
            BookingStatus.CONFIRMED,
            BookingStatus.CANCELLATION_PENDING,
            BookingStatus.CANCELLED
    );

    private static final String SYSTEM_INITIATOR = "System";

    private static final String EVENT_TYPE_BOOKING_JOB_CONFIRMED = "BookingJobConfirmed";
    private static final String EVENT_TYPE_BOOKING_JOB_DENIED = "BookingJobDenied";
    private static final String EVENT_TYPE_CANCEL_BOOKING_ERROR = "CancelBookingJobError";

    private final BookingRepository bookingRepository;
    private final BookingStatusHistoryRepository bookingStatusHistoryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final BookingEventPublisher bookingEventPublisher;
    private final CurrentDateTimeProvider dateTimeProvider;

    // === КОМАНДЫ (Use Cases) ===

    /**
     * Создать новое бронирование
     * Отправляет асинхронную команду в Catalog Service для создания booking job
     *
     * @return ID созданного бронирования
     */
    public Long createBooking(Long userId, Long resourceId, LocalDate bookedFrom, LocalDate bookedTo) {
        OffsetDateTime now = dateTimeProvider.utcNow();
        Booking booking = Booking.create(userId, resourceId, bookedFrom, bookedTo, now);

        UUID requestId = UUID.randomUUID();
        booking.setCatalogRequestId(requestId);

        booking = bookingRepository.save(booking);
        saveStatusHistory(booking, null, booking.getStatus(), now,
                "Создание бронирования", String.valueOf(userId));

        CreateBookingJobRequest command = new CreateBookingJobRequest(
                UUID.randomUUID(),
                requestId,
                booking.getResourceId(),
                booking.getBookedFrom(),
                booking.getBookedTo()
        );

        bookingEventPublisher.publishCreateBookingJob(command);

        log.info("Создано бронирование с ID: {} и requestId: {}", booking.getId(), requestId);
        return booking.getId();
    }

    /**
     * Отменить бронирование
     * Отправляет асинхронную команду в Catalog Service для отмены booking job
     *
     * @param id идентификатор бронирования
     */
    public void cancelBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Бронирование с указанным id: '" + id + "' не найдено."));

        BookingStatus previousStatus = booking.getStatus();
        OffsetDateTime now = dateTimeProvider.utcNow();
        booking.startCancellation(now);

        bookingRepository.save(booking);
        String reason = "Пользователь запросил отмену бронирования";
        saveStatusHistory(booking, previousStatus, booking.getStatus(), now,
                reason, String.valueOf(booking.getUserId()));
        publishStatusChangedEvent(booking, previousStatus, now, reason);

        if (booking.getCatalogRequestId() != null) {
            CancelBookingJobByRequestIdRequest command = new CancelBookingJobByRequestIdRequest(
                    UUID.randomUUID(),
                    booking.getCatalogRequestId()
            );

            bookingEventPublisher.publishCancelBookingJob(command);
        }

        log.info("Начата отмена бронирования с ID: {}, статус: CANCELLATION_PENDING", id);
    }

    // === ЗАПРОСЫ (Queries) ===

    /**
     * Получить бронирование по ID
     *
     * @param id идентификатор бронирования
     * @return бронирование
     */
    @Transactional(readOnly = true)
    public Booking getById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Бронирование с указанным id: '" + id + "' не найдено."));
    }

    /**
     * Получить бронирования по фильтрам с пагинацией
     *
     * @param userId идентификатор пользователя (опционально)
     * @param resourceId идентификатор ресурса (опционально)
     * @param status статус бронирования (опционально)
     * @param pageNumber номер страницы
     * @param pageSize размер страницы
     * @return страница с бронированиями
     */
    @Transactional(readOnly = true)
    public List<Booking> getByFilter(Long userId, Long resourceId, BookingStatus status,
                                     int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        return bookingRepository.findByFilter(userId, resourceId, status, pageable);
    }

    /**
     * Получить только статус бронирования по ID
     *
     * @param id идентификатор бронирования
     * @return статус бронирования или null
     */
    @Transactional(readOnly = true)
    public BookingStatus getStatusById(Long id) {
        return bookingRepository.findStatusById(id);
    }

    /**
     * Получить историю изменений статуса бронирования.
     *
     * @param id идентификатор бронирования
     * @param pageNumber номер страницы
     * @param pageSize размер страницы
     * @return страница записей истории
     */
    @Transactional(readOnly = true)
    public BookingStatusHistoryPageResponse getStatusHistory(Long id, int page, int pageSize) {
        if (!bookingRepository.existsById(id)) {
            throw new BusinessException("Бронирование с указанным id: '" + id + "' не найдено.");
        }
        if (page < 1) {
            throw new BusinessException("Параметр page должен быть больше или равен 1");
        }
        if (pageSize < 1) {
            throw new BusinessException("Параметр pageSize должен быть больше или равен 1");
        }

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "changedAt"));
        Page<BookingStatusHistory> historyPage = bookingStatusHistoryRepository.findByBookingId(id, pageable);
        List<BookingStatusHistoryResponse> items = historyPage.getContent()
                .stream()
                .map(this::toStatusHistoryResponse)
                .toList();

        return new BookingStatusHistoryPageResponse(id, historyPage.getTotalElements(), items);
    }

    /**
     * Получить агрегированную статистику по бронированиям за период создания.
     *
     * @param dateFrom дата начала периода включительно
     * @param dateTo дата окончания периода включительно
     * @return статистика бронирований
     */
    @Transactional(readOnly = true)
    public BookingStatisticsResponse getStatistics(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null) {
            throw new BusinessException("Параметры dateFrom и dateTo обязательны");
        }
        if (dateTo.isBefore(dateFrom)) {
            throw new BusinessException("dateTo не может быть раньше dateFrom");
        }

        OffsetDateTime createdAtFrom = dateFrom.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime createdAtTo = dateTo.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        long totalBookings = bookingRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                createdAtFrom, createdAtTo);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (BookingStatus status : STATISTICS_STATUSES) {
            byStatus.put(toStatisticsStatusKey(status), 0L);
        }
        bookingRepository.countByStatus(createdAtFrom, createdAtTo)
                .forEach(row -> {
                    String statusKey = toStatisticsStatusKey(row.getStatus());
                    if (statusKey != null) {
                        byStatus.put(statusKey, row.getBookingCount());
                    }
                });

        List<TopResourceResponse> topResources = bookingRepository
                .findPopularResources(createdAtFrom, createdAtTo, PageRequest.of(0, 5))
                .stream()
                .map(row -> new TopResourceResponse(row.getResourceId(), row.getBookingCount()))
                .toList();

        return new BookingStatisticsResponse(totalBookings, byStatus, topResources);
    }

    private String toStatisticsStatusKey(BookingStatus status) {
        return switch (status) {
            case AWAIT_CONFIRMATION -> "awaitConfirmation";
            case CONFIRMED -> "confirmed";
            case CANCELLATION_PENDING -> "cancellationPending";
            case CANCELLED -> "cancelled";
            case NONE -> null;
        };
    }

    // === EVENT HANDLERS (Обработка асинхронных событий от Catalog Service) ===

    /**
     * Обработать событие подтверждения booking job от Catalog Service
     * Обновляет статус бронирования на CONFIRMED.
     * <p>
     * Идемпотентно: повторная обработка одного и того же {@code eventId} безопасна.
     * При гонке двух instance UNIQUE-нарушение пробросится в виде
     * {@link org.springframework.dao.DataIntegrityViolationException} —
     * вызывающий код должен трактовать его как «уже обработано».
     */
    @Transactional
    public void handleBookingJobConfirmed(UUID eventId, UUID requestId) {
        log.info("Получено событие BookingJobConfirmed: eventId={}, requestId={}", eventId, requestId);

        if (isAlreadyProcessed(eventId)) {
            return;
        }

        Booking booking = bookingRepository.findByCatalogRequestId(requestId).orElse(null);
        if (booking == null) {
            log.warn("Бронирование не найдено по requestId: {}. Событие проигнорировано.", requestId);
            markEventProcessed(eventId, EVENT_TYPE_BOOKING_JOB_CONFIRMED);
            return;
        }

        log.info("Найдено бронирование: id={}, статус={}. Подтверждаем...",
                booking.getId(), booking.getStatus());

        if (booking.getStatus() == BookingStatus.CANCELLATION_PENDING) {
            log.warn("Обнаружен race condition при подтверждении бронирования: id={}, requestId={}",
                    booking.getId(), requestId);
        }

        BookingStatus previousStatus = booking.getStatus();
        OffsetDateTime now = dateTimeProvider.utcNow();
        booking.confirm();
        bookingRepository.save(booking);
        String reason = "Catalog Service подтвердил бронирование";
        saveStatusHistory(booking, previousStatus, booking.getStatus(), now,
                reason, SYSTEM_INITIATOR);
        publishStatusChangedEvent(booking, previousStatus, now, reason);

        markEventProcessed(eventId, EVENT_TYPE_BOOKING_JOB_CONFIRMED);

        log.info("Бронирование успешно подтверждено: id={}, новый статус={}",
                booking.getId(), booking.getStatus());
    }

    /**
     * Обработать событие отклонения booking job от Catalog Service
     * Отменяет бронирование.
     * <p>
     * Идемпотентно: повторная обработка одного и того же {@code eventId} безопасна.
     */
    @Transactional
    public void handleBookingJobDenied(UUID eventId, UUID requestId) {
        log.info("Получено событие BookingJobDenied: eventId={}, requestId={}", eventId, requestId);

        if (isAlreadyProcessed(eventId)) {
            return;
        }

        Booking booking = bookingRepository.findByCatalogRequestId(requestId).orElse(null);
        if (booking == null) {
            log.warn("Бронирование не найдено по requestId: {}. Событие проигнорировано.", requestId);
            markEventProcessed(eventId, EVENT_TYPE_BOOKING_JOB_DENIED);
            return;
        }

        log.info("Найдено бронирование: id={}, статус={}. Отменяем...",
                booking.getId(), booking.getStatus());

        BookingStatus previousStatus = booking.getStatus();
        OffsetDateTime now = dateTimeProvider.utcNow();
        LocalDate currentDate = LocalDate.from(now);
        booking.cancel(currentDate);
        bookingRepository.save(booking);
        String reason = "Catalog Service отклонил бронирование";
        saveStatusHistory(booking, previousStatus, booking.getStatus(), now,
                reason, SYSTEM_INITIATOR);
        publishStatusChangedEvent(booking, previousStatus, now, reason);

        markEventProcessed(eventId, EVENT_TYPE_BOOKING_JOB_DENIED);

        log.info("Бронирование успешно отменено: id={}, новый статус={}",
                booking.getId(), booking.getStatus());
    }

    /**
     * Обработать событие ошибки от Catalog Service из DLQ.
     * <p>
     * Идемпотентно: повторная обработка одного и того же {@code eventId} безопасна.
     */
    @Transactional
    public void handleError(UUID eventId, UUID requestId) {
        log.info("Получено событие ошибки из DLQ: eventId={}, requestId={}", eventId, requestId);

        if (isAlreadyProcessed(eventId)) {
            return;
        }

        Booking booking = bookingRepository.findByCatalogRequestId(requestId).orElse(null);
        if (booking == null) {
            log.warn("Бронирование не найдено по requestId: {}. Событие проигнорировано.", requestId);
            markEventProcessed(eventId, EVENT_TYPE_CANCEL_BOOKING_ERROR);
            return;
        }

        BookingStatus previousStatus = booking.getStatus();
        OffsetDateTime now = dateTimeProvider.utcNow();
        booking.rollbackCancellation();
        bookingRepository.save(booking);
        saveStatusHistory(booking, previousStatus, booking.getStatus(), now,
                "Catalog Service не смог обработать отмену", SYSTEM_INITIATOR);

        markEventProcessed(eventId, EVENT_TYPE_CANCEL_BOOKING_ERROR);

        log.info("❌ Отмена не удалась, статус возвращен: id={}, статус={}",
                booking.getId(), booking.getStatus());
    }

    private boolean isAlreadyProcessed(UUID eventId) {
        if (eventId == null) {
            log.warn("Получено событие без eventId — идемпотентность отключена для этого сообщения");
            return false;
        }
        if (processedEventRepository.existsById(eventId)) {
            log.warn("Событие уже было обработано ранее (дубликат): eventId={}", eventId);
            return true;
        }
        return false;
    }

    private void markEventProcessed(UUID eventId, String eventType) {
        if (eventId == null) {
            return;
        }
        ProcessedEvent processed = ProcessedEvent.create(eventId, eventType, dateTimeProvider.utcNow());
        processedEventRepository.saveAndFlush(processed);
    }

    private void publishStatusChangedEvent(Booking booking,
                                           BookingStatus previousStatus,
                                           OffsetDateTime changedAt,
                                           String reason) {
        BookingStatusChangedEvent event = new BookingStatusChangedEvent(
                UUID.randomUUID(),
                booking.getId(),
                previousStatus,
                booking.getStatus(),
                changedAt,
                reason
        );
        bookingEventPublisher.publishBookingStatusChanged(event);
    }

    private void saveStatusHistory(Booking booking,
                                   BookingStatus previousStatus,
                                   BookingStatus newStatus,
                                   OffsetDateTime changedAt,
                                   String reason,
                                   String initiator) {
        BookingStatusHistory history = BookingStatusHistory.create(
                booking,
                previousStatus,
                newStatus,
                changedAt,
                reason,
                initiator
        );
        bookingStatusHistoryRepository.save(history);
    }

    private BookingStatusHistoryResponse toStatusHistoryResponse(BookingStatusHistory history) {
        return new BookingStatusHistoryResponse(
                history.getId(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getChangedAt(),
                history.getReason(),
                history.getInitiator()
        );
    }
}
