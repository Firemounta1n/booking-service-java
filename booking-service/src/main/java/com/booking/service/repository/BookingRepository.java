package com.booking.service.repository;

import com.booking.service.entity.Booking;
import com.booking.service.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA репозиторий для работы с бронированиями
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    interface BookingStatusCount {
        BookingStatus getStatus();

        long getBookingCount();
    }

    interface ResourceBookingCount {
        Long getResourceId();

        long getBookingCount();
    }

    /**
     * Найти бронирование по идентификатору запроса в Catalog Service
     * @param catalogRequestId идентификатор запроса
     * @return Optional с бронированием или empty
     */
    Optional<Booking> findByCatalogRequestId(UUID catalogRequestId);

    /**
     * Найти бронирования по фильтрам с пагинацией
     * Используется для получения списка бронирований с опциональными фильтрами
     *
     * @param userId идентификатор пользователя (опционально)
     * @param resourceId идентификатор ресурса (опционально)
     * @param status статус бронирования (опционально)
     * @param pageable параметры пагинации и сортировки
     * @return Page с результатами
     */
    @Query("SELECT b FROM Booking b WHERE " +
           "(:userId IS NULL OR b.userId = :userId) AND " +
           "(:resourceId IS NULL OR b.resourceId = :resourceId) AND " +
           "(:status IS NULL OR b.status = :status)")
    List<Booking> findByFilter(@Param("userId") Long userId,
                               @Param("resourceId") Long resourceId,
                               @Param("status") BookingStatus status,
                               Pageable pageable);

    /**
     * Получить только статус бронирования по ID
     * @param id идентификатор бронирования
     * @return статус бронирования или null
     */
    @Query("SELECT b.status FROM Booking b WHERE b.id = :id")
    BookingStatus findStatusById(@Param("id") Long id);

    /**
     * Посчитать общее количество бронирований за период создания.
     */
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(OffsetDateTime createdAtFrom,
                                                              OffsetDateTime createdAtTo);

    /**
     * Посчитать количество бронирований за период создания в разрезе статусов.
     */
    @Query("SELECT b.status AS status, COUNT(b) AS bookingCount FROM Booking b " +
           "WHERE b.createdAt >= :createdAtFrom AND b.createdAt < :createdAtTo " +
           "GROUP BY b.status")
    List<BookingStatusCount> countByStatus(@Param("createdAtFrom") OffsetDateTime createdAtFrom,
                                           @Param("createdAtTo") OffsetDateTime createdAtTo);

    /**
     * Найти самые популярные ресурсы за период создания.
     */
    @Query("SELECT b.resourceId AS resourceId, COUNT(b) AS bookingCount FROM Booking b " +
           "WHERE b.createdAt >= :createdAtFrom AND b.createdAt < :createdAtTo " +
           "GROUP BY b.resourceId " +
           "ORDER BY COUNT(b) DESC, b.resourceId ASC")
    List<ResourceBookingCount> findPopularResources(@Param("createdAtFrom") OffsetDateTime createdAtFrom,
                                                    @Param("createdAtTo") OffsetDateTime createdAtTo,
                                                    Pageable pageable);

    /**
     * Найти зависшие отмены для повторной отправки команды отмены.
     */
    @Query("SELECT b FROM Booking b " +
           "WHERE b.status = :status " +
           "AND b.cancellationSentAt <= :sentBefore " +
           "AND b.catalogRequestId IS NOT NULL")
    List<Booking> findStaleCancellations(@Param("status") BookingStatus status,
                                         @Param("sentBefore") OffsetDateTime sentBefore);
}
