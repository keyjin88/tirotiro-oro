package oro.tirotiro.equipmentwarehouse.booking.persistence;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public final class BookingSpecifications {

    private BookingSpecifications() {
    }

    public static Specification<Booking> fromCriteria(BookingSearchCriteria criteria) {
        Specification<Booking> spec = (root, query, cb) -> cb.conjunction();
        if (criteria.userId() != null) {
            spec = spec.and(userIdEquals(criteria.userId()));
        }
        if (criteria.status() != null) {
            spec = spec.and(statusEquals(criteria.status()));
        }
        if (criteria.rangeStart() != null) {
            spec = spec.and(endsAfter(criteria.rangeStart()));
        }
        if (criteria.rangeEnd() != null) {
            spec = spec.and(startsBefore(criteria.rangeEnd()));
        }
        if (criteria.equipmentItemId() != null) {
            spec = spec.and(hasEquipmentItem(criteria.equipmentItemId()));
        }
        return spec;
    }

    private static Specification<Booking> userIdEquals(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    private static Specification<Booking> statusEquals(BookingStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static Specification<Booking> endsAfter(java.time.Instant rangeStart) {
        return (root, query, cb) -> cb.greaterThan(root.get("endsAt"), rangeStart);
    }

    private static Specification<Booking> startsBefore(java.time.Instant rangeEnd) {
        return (root, query, cb) -> cb.lessThan(root.get("startsAt"), rangeEnd);
    }

    private static Specification<Booking> hasEquipmentItem(UUID equipmentItemId) {
        return (root, query, cb) -> {
            Subquery<Long> lineExists = query.subquery(Long.class);
            Root<BookingLine> line = lineExists.from(BookingLine.class);
            lineExists.select(cb.literal(1L));
            lineExists.where(
                    cb.equal(line.get("booking"), root),
                    cb.equal(line.get("equipmentItem").get("id"), equipmentItemId));
            return cb.exists(lineExists);
        };
    }
}
