package oro.tirotiro.equipmentwarehouse.booking;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import oro.tirotiro.equipmentwarehouse.booking.persistence.Booking;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingRepository;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingStatus;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItem;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnit;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitStatus;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.TrackingMode;

@Service
public class AvailabilityService {

    private static final Set<BookingStatus> BLOCKING_STATUSES = Set.of(BookingStatus.BOOKED);

    private final EquipmentItemRepository itemRepository;
    private final EquipmentUnitRepository unitRepository;
    private final BookingRepository bookingRepository;

    public AvailabilityService(
            EquipmentItemRepository itemRepository,
            EquipmentUnitRepository unitRepository,
            BookingRepository bookingRepository) {
        this.itemRepository = itemRepository;
        this.unitRepository = unitRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public List<ItemAvailability> getAvailability(Collection<UUID> itemIds, Instant startsAt, Instant endsAt) {
        validatePeriod(startsAt, endsAt);
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        List<EquipmentItem> items = itemRepository.findAllById(itemIds);
        List<Booking> overlapping = bookingRepository.findOverlappingForItems(
                itemIds,
                startsAt,
                endsAt,
                BLOCKING_STATUSES);
        Occupancy occupancy = occupancyFrom(overlapping, null);
        List<ItemAvailability> result = new ArrayList<>();
        for (EquipmentItem item : items) {
            if (item.getTrackingMode() == TrackingMode.QUANTITY) {
                int booked = occupancy.quantityByItem.getOrDefault(item.getId(), 0);
                result.add(new ItemAvailability(item.getId(), item.getTotalQuantity(), booked,
                        Math.max(0, item.getTotalQuantity() - booked), List.of()));
            } else {
                List<UnitAvailability> unitAvailability = unitRepository.findByEquipmentItem_IdAndArchivedFalse(item.getId())
                        .stream()
                        .map(unit -> new UnitAvailability(
                                unit.getId(),
                                unit.getInventoryNumber(),
                                unit.getStatus(),
                                unit.getStatus() == EquipmentUnitStatus.AVAILABLE
                                        && !occupancy.bookedUnitIds.contains(unit.getId())))
                        .toList();
                int available = (int) unitAvailability.stream().filter(UnitAvailability::available).count();
                result.add(new ItemAvailability(item.getId(), unitAvailability.size(),
                        unitAvailability.size() - available, available, unitAvailability));
            }
        }
        return result;
    }

    public void assertAvailable(
            Collection<EquipmentItem> lockedItems,
            Collection<EquipmentUnit> lockedUnits,
            Collection<RequestedLine> requestedLines,
            Instant startsAt,
            Instant endsAt,
            Collection<Booking> overlappingBookings,
            UUID ignoredBookingId) {
        validatePeriod(startsAt, endsAt);
        if (requestedLines == null || requestedLines.isEmpty()) {
            throw new AvailabilityException("Бронирование должно содержать хотя бы одну позицию");
        }

        Map<UUID, EquipmentItem> itemsById = lockedItems.stream()
                .collect(Collectors.toMap(EquipmentItem::getId, Function.identity()));
        Map<UUID, EquipmentUnit> unitsById = lockedUnits.stream()
                .collect(Collectors.toMap(EquipmentUnit::getId, Function.identity()));
        Map<UUID, Integer> requestedQuantityByItem = new HashMap<>();
        Set<UUID> requestedUnitIds = new HashSet<>();

        for (RequestedLine line : requestedLines) {
            EquipmentItem item = itemsById.get(line.equipmentItemId());
            if (item == null || !item.isActive()) {
                throw new AvailabilityException("Оборудование недоступно для бронирования: " + line.equipmentItemId());
            }
            if (line.equipmentUnitId() == null) {
                validateQuantityLine(line, item);
                requestedQuantityByItem.merge(item.getId(), line.quantity(), Integer::sum);
            } else {
                validateUnitLine(line, item, unitsById, requestedUnitIds);
            }
        }

        Occupancy occupancy = occupancyFrom(overlappingBookings, ignoredBookingId);
        requestedQuantityByItem.forEach((itemId, requestedQuantity) -> {
            EquipmentItem item = itemsById.get(itemId);
            int bookedQuantity = occupancy.quantityByItem.getOrDefault(itemId, 0);
            int availableQuantity = item.getTotalQuantity() - bookedQuantity;
            if (requestedQuantity > availableQuantity) {
                throw new AvailabilityException(String.format(
                        "Недостаточно «%s»: запрошено %d, доступно %d",
                        item.getName(),
                        requestedQuantity,
                        availableQuantity));
            }
        });
        for (UUID requestedUnitId : requestedUnitIds) {
            if (occupancy.bookedUnitIds.contains(requestedUnitId)) {
                throw new AvailabilityException("Единица оборудования уже забронирована: " + requestedUnitId);
            }
        }
    }

    public Set<BookingStatus> blockingStatuses() {
        return BLOCKING_STATUSES;
    }

    public void validatePeriod(Instant startsAt, Instant endsAt) {
        if (startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("Начало бронирования должно быть раньше окончания");
        }
    }

    private void validateQuantityLine(RequestedLine line, EquipmentItem item) {
        if (item.getTrackingMode() != TrackingMode.QUANTITY) {
            throw new AvailabilityException("Для позиций с поштучным учетом нужно выбрать конкретную единицу оборудования: " + item.getId());
        }
        if (line.quantity() < 1) {
            throw new AvailabilityException("Запрошенное количество должно быть положительным");
        }
    }

    private void validateUnitLine(
            RequestedLine line,
            EquipmentItem item,
            Map<UUID, EquipmentUnit> unitsById,
            Set<UUID> requestedUnitIds) {
        if (item.getTrackingMode() != TrackingMode.UNIT) {
            throw new AvailabilityException("Для позиций с количественным учетом нельзя указывать единицу оборудования: " + item.getId());
        }
        if (line.quantity() != 1) {
            throw new AvailabilityException("Для поштучного учета количество в строке бронирования должно быть 1");
        }
        EquipmentUnit unit = unitsById.get(line.equipmentUnitId());
        if (unit == null
                || !unit.getEquipmentItem().getId().equals(item.getId())
                || unit.isArchived()
                || unit.getStatus() != EquipmentUnitStatus.AVAILABLE) {
            throw new AvailabilityException("Единица оборудования недоступна для бронирования: " + line.equipmentUnitId());
        }
        if (!requestedUnitIds.add(unit.getId())) {
            throw new AvailabilityException("Единица оборудования запрошена больше одного раза: " + unit.getId());
        }
    }

    private Occupancy occupancyFrom(Collection<Booking> bookings, UUID ignoredBookingId) {
        Map<UUID, Integer> quantityByItem = new HashMap<>();
        Set<UUID> bookedUnitIds = new HashSet<>();
        if (bookings == null) {
            return new Occupancy(quantityByItem, bookedUnitIds);
        }
        for (Booking booking : bookings) {
            if (booking.getStatus() != BookingStatus.BOOKED || Objects.equals(booking.getId(), ignoredBookingId)) {
                continue;
            }
            booking.getLines().forEach(line -> {
                if (line.getEquipmentUnit() == null) {
                    quantityByItem.merge(line.getEquipmentItem().getId(), line.getQuantity(), Integer::sum);
                } else {
                    bookedUnitIds.add(line.getEquipmentUnit().getId());
                }
            });
        }
        return new Occupancy(quantityByItem, bookedUnitIds);
    }

    private record Occupancy(Map<UUID, Integer> quantityByItem, Set<UUID> bookedUnitIds) {
    }

    public record RequestedLine(UUID equipmentItemId, UUID equipmentUnitId, int quantity) {
    }

    public record ItemAvailability(
            UUID equipmentItemId,
            int total,
            int booked,
            int available,
            List<UnitAvailability> units) {
    }

    public record UnitAvailability(
            UUID equipmentUnitId,
            String inventoryNumber,
            EquipmentUnitStatus status,
            boolean available) {
    }
}
