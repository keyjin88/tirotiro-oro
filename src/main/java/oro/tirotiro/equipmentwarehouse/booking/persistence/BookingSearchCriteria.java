package oro.tirotiro.equipmentwarehouse.booking.persistence;

import java.time.Instant;
import java.util.UUID;

public record BookingSearchCriteria(
        UUID userId,
        BookingStatus status,
        Instant rangeStart,
        Instant rangeEnd,
        UUID equipmentItemId) {

    public static BookingSearchCriteria unrestricted() {
        return new BookingSearchCriteria(null, null, null, null, null);
    }
}
