package oro.tirotiro.equipmentwarehouse.booking.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @EntityGraph(attributePaths = {"user", "lines", "lines.equipmentItem", "lines.equipmentUnit"})
    List<Booking> findByUser_IdOrderByStartsAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"user", "lines", "lines.equipmentItem", "lines.equipmentUnit"})
    List<Booking> findAllByOrderByStartsAtDesc();

    @EntityGraph(attributePaths = {"user", "lines", "lines.equipmentItem", "lines.equipmentUnit"})
    List<Booking> findByStatusAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAsc(
            BookingStatus status,
            Instant endsAt,
            Instant startsAt);

    @Query("""
            select distinct booking
            from Booking booking
            join fetch booking.lines line
            where booking.status in :statuses
              and booking.startsAt < :endsAt
              and booking.endsAt > :startsAt
              and line.equipmentItem.id in :equipmentItemIds
            """)
    List<Booking> findOverlappingForItems(
            @Param("equipmentItemIds") Collection<UUID> equipmentItemIds,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("statuses") Collection<BookingStatus> statuses);
}
