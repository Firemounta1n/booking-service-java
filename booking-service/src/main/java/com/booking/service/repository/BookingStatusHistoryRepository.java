package com.booking.service.repository;

import com.booking.service.entity.BookingStatusHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA репозиторий для истории изменений статусов бронирования.
 */
@Repository
public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, Long> {

    Page<BookingStatusHistory> findByBookingId(Long bookingId, Pageable pageable);
}
