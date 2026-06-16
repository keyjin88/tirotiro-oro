package oro.tirotiro.equipmentwarehouse.booking.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    boolean existsByUser_Id(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Booking booking set booking.cancelledBy = null where booking.cancelledBy.id = :userId")
    void clearCancelledByReferences(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = {
            "user",
            "lines",
            "lines.equipmentItem",
            "lines.equipmentItem.category",
            "lines.equipmentItem.owner",
            "lines.equipmentUnit"})
    List<Booking> findByUser_IdOrderByStartsAtDesc(UUID userId);

    @EntityGraph(attributePaths = {
            "user",
            "lines",
            "lines.equipmentItem",
            "lines.equipmentItem.category",
            "lines.equipmentItem.owner",
            "lines.equipmentUnit"})
    List<Booking> findAllByOrderByStartsAtDesc();

    @EntityGraph(attributePaths = {
            "user",
            "lines",
            "lines.equipmentItem",
            "lines.equipmentItem.category",
            "lines.equipmentItem.owner",
            "lines.equipmentUnit"})
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

    @EntityGraph(attributePaths = {
            "user",
            "lines",
            "lines.equipmentItem",
            "lines.equipmentItem.category",
            "lines.equipmentItem.owner",
            "lines.equipmentUnit"})
    List<Booking> findAll(Specification<Booking> spec, Sort sort);
}
