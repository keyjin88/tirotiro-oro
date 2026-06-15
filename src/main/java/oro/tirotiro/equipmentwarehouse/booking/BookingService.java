package oro.tirotiro.equipmentwarehouse.booking;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import oro.tirotiro.equipmentwarehouse.audit.AuditService;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.booking.persistence.Booking;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingLine;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingRepository;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingStatus;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItem;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnit;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitRepository;
import oro.tirotiro.equipmentwarehouse.permission.PermissionService;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EquipmentItemRepository itemRepository;
    private final EquipmentUnitRepository unitRepository;
    private final AvailabilityService availabilityService;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final Clock clock;

    public BookingService(
            BookingRepository bookingRepository,
            EquipmentItemRepository itemRepository,
            EquipmentUnitRepository unitRepository,
            AvailabilityService availabilityService,
            PermissionService permissionService,
            AuditService auditService,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.itemRepository = itemRepository;
        this.unitRepository = unitRepository;
        this.availabilityService = availabilityService;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Booking> visibleBookings(User actor) {
        if (permissionService.isAdmin(actor)) {
            return bookingRepository.findAllByOrderByStartsAtDesc();
        }
        return bookingRepository.findByUser_IdOrderByStartsAtDesc(actor.getId());
    }

    @Transactional
    public Booking createBooking(CreateBookingCommand command, User actor) {
        availabilityService.validatePeriod(command.startsAt(), command.endsAt());
        LockedSelection locked = lockSelection(command.lines());
        List<Booking> overlapping = bookingRepository.findOverlappingForItems(
                locked.itemIds(),
                command.startsAt(),
                command.endsAt(),
                availabilityService.blockingStatuses());
        availabilityService.assertAvailable(
                locked.items(),
                locked.units(),
                toRequestedLines(command.lines()),
                command.startsAt(),
                command.endsAt(),
                overlapping,
                null);

        Booking booking = new Booking(actor, command.startsAt(), command.endsAt(), BookingStatus.BOOKED);
        booking.setComment(command.comment());
        booking.replaceLines(toBookingLines(booking, command.lines(), locked.itemsById(), locked.unitsById()));
        Booking saved = bookingRepository.save(booking);
        auditService.record(actor, "BOOKING_CREATED", "BOOKING", saved.getId(), Map.of(
                "startsAt", saved.getStartsAt().toString(),
                "endsAt", saved.getEndsAt().toString(),
                "lineCount", saved.getLines().size()));
        return saved;
    }

    @Transactional
    public Booking updateBooking(UUID bookingId, UpdateBookingCommand command, User actor) {
        Booking booking = requireBooking(bookingId);
        requireBookingEditable(booking, actor);
        availabilityService.validatePeriod(command.startsAt(), command.endsAt());

        Set<BookingLineCommand> requestedLines = new HashSet<>(command.lines());
        booking.getLines().forEach(line -> requestedLines.add(new BookingLineCommand(
                line.getEquipmentItem().getId(),
                line.getEquipmentUnit() == null ? null : line.getEquipmentUnit().getId(),
                line.getQuantity())));
        LockedSelection locked = lockSelection(requestedLines);
        List<Booking> overlapping = bookingRepository.findOverlappingForItems(
                locked.itemIds(),
                command.startsAt(),
                command.endsAt(),
                availabilityService.blockingStatuses());
        availabilityService.assertAvailable(
                locked.items(),
                locked.units(),
                toRequestedLines(command.lines()),
                command.startsAt(),
                command.endsAt(),
                overlapping,
                booking.getId());

        booking.reschedule(command.startsAt(), command.endsAt());
        booking.setComment(command.comment());
        booking.replaceLines(toBookingLines(booking, command.lines(), locked.itemsById(), locked.unitsById()));
        auditService.record(actor, "BOOKING_UPDATED", "BOOKING", booking.getId(), Map.of(
                "startsAt", booking.getStartsAt().toString(),
                "endsAt", booking.getEndsAt().toString(),
                "lineCount", booking.getLines().size()));
        return booking;
    }

    @Transactional
    public Booking cancelBooking(UUID bookingId, String reason, User actor) {
        Booking booking = requireBooking(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return booking;
        }
        boolean admin = permissionService.isAdmin(actor);
        if (!admin) {
            if (!booking.getUser().getId().equals(actor.getId())) {
                throw new AccessDeniedException("Пользователи могут отменять только свои бронирования");
            }
            if (!booking.getStartsAt().isAfter(clock.instant())) {
                throw new IllegalArgumentException("Владелец может отменять только будущие бронирования");
            }
        }
        if (admin && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Администратор должен указать причину отмены");
        }

        booking.cancel(actor, reason, clock.instant());
        auditService.record(actor, admin ? "BOOKING_ADMIN_CANCELLED" : "BOOKING_CANCELLED", "BOOKING", booking.getId(),
                Map.of("reason", reason == null ? "" : reason));
        return booking;
    }

    private Booking requireBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Бронирование не найдено: " + bookingId));
    }

    private void requireBookingEditable(Booking booking, User actor) {
        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw new IllegalArgumentException("Изменять можно только активные бронирования");
        }
        if (!permissionService.isAdmin(actor) && !booking.getUser().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Пользователи могут изменять только свои бронирования");
        }
    }

    private LockedSelection lockSelection(Collection<BookingLineCommand> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new AvailabilityException("Бронирование должно содержать хотя бы одну позицию");
        }
        List<UUID> itemIds = lines.stream()
                .map(BookingLineCommand::equipmentItemId)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<UUID> unitIds = lines.stream()
                .map(BookingLineCommand::equipmentUnitId)
                .filter(id -> id != null)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<EquipmentItem> items = itemRepository.lockByIdInOrderById(itemIds);
        List<EquipmentUnit> units = unitIds.isEmpty() ? List.of() : unitRepository.lockByIdInOrderById(unitIds);
        if (items.size() != itemIds.size()) {
            throw new AvailabilityException("Одна или несколько позиций оборудования не найдены");
        }
        if (units.size() != unitIds.size()) {
            throw new AvailabilityException("Одна или несколько единиц оборудования не найдены");
        }
        return new LockedSelection(
                itemIds,
                items,
                units,
                items.stream().collect(Collectors.toMap(EquipmentItem::getId, Function.identity())),
                units.stream().collect(Collectors.toMap(EquipmentUnit::getId, Function.identity())));
    }

    private List<AvailabilityService.RequestedLine> toRequestedLines(Collection<BookingLineCommand> lines) {
        return lines.stream()
                .map(line -> new AvailabilityService.RequestedLine(
                        line.equipmentItemId(),
                        line.equipmentUnitId(),
                        line.quantity()))
                .toList();
    }

    private Set<BookingLine> toBookingLines(
            Booking booking,
            Collection<BookingLineCommand> lines,
            Map<UUID, EquipmentItem> itemsById,
            Map<UUID, EquipmentUnit> unitsById) {
        return lines.stream()
                .map(line -> new BookingLine(
                        booking,
                        itemsById.get(line.equipmentItemId()),
                        line.equipmentUnitId() == null ? null : unitsById.get(line.equipmentUnitId()),
                        line.quantity()))
                .collect(Collectors.toSet());
    }

    private record LockedSelection(
            List<UUID> itemIds,
            List<EquipmentItem> items,
            List<EquipmentUnit> units,
            Map<UUID, EquipmentItem> itemsById,
            Map<UUID, EquipmentUnit> unitsById) {
    }

    public record CreateBookingCommand(
            Instant startsAt,
            Instant endsAt,
            String comment,
            List<BookingLineCommand> lines) {
    }

    public record UpdateBookingCommand(
            Instant startsAt,
            Instant endsAt,
            String comment,
            List<BookingLineCommand> lines) {
    }

    public record BookingLineCommand(UUID equipmentItemId, UUID equipmentUnitId, int quantity) {
    }
}
