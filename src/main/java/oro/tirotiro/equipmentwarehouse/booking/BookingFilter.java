package oro.tirotiro.equipmentwarehouse.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingStatus;

public record BookingFilter(
        BookingStatus status,
        UUID userId,
        LocalDate fromDate,
        LocalDate toDate,
        UUID equipmentItemId) {

    public static final BookingFilter EMPTY = new BookingFilter(null, null, null, null, null);

    public static BookingFilter of(
            BookingStatus status,
            UUID userId,
            LocalDate fromDate,
            LocalDate toDate,
            UUID equipmentItemId) {
        return new BookingFilter(status, userId, fromDate, toDate, equipmentItemId);
    }

    public List<BookingStatus> statuses() {
        return status == null ? null : List.of(status);
    }

    public boolean filterByUser() {
        return userId != null;
    }

    public boolean filterByStatus() {
        return status != null;
    }

    public boolean filterByFromDate() {
        return fromDate != null;
    }

    public boolean filterByToDate() {
        return toDate != null;
    }

    public boolean filterByEquipment() {
        return equipmentItemId != null;
    }
}
