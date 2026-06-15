package oro.tirotiro.equipmentwarehouse.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import oro.tirotiro.equipmentwarehouse.booking.AvailabilityService;
import oro.tirotiro.equipmentwarehouse.config.AppProperties;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItem;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;

@Service
public class CalendarService {

    private final EquipmentItemRepository itemRepository;
    private final AvailabilityService availabilityService;
    private final ZoneId zoneId;

    public CalendarService(
            EquipmentItemRepository itemRepository,
            AvailabilityService availabilityService,
            AppProperties appProperties) {
        this.itemRepository = itemRepository;
        this.availabilityService = availabilityService;
        this.zoneId = appProperties.timeZone();
    }

    @Transactional(readOnly = true)
    public CalendarView availabilityCalendar(Instant startsAt, Instant endsAt, UUID categoryId) {
        availabilityService.validatePeriod(startsAt, endsAt);
        List<EquipmentItem> items = itemRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .filter(item -> categoryId == null || item.getCategory().getId().equals(categoryId))
                .toList();
        List<UUID> itemIds = items.stream().map(EquipmentItem::getId).toList();
        Map<UUID, AvailabilityService.ItemAvailability> availabilityByItem = availabilityService
                .getAvailability(itemIds, startsAt, endsAt)
                .stream()
                .collect(Collectors.toMap(AvailabilityService.ItemAvailability::equipmentItemId, Function.identity()));

        List<CalendarItem> calendarItems = items.stream()
                .map(item -> {
                    AvailabilityService.ItemAvailability availability = availabilityByItem.get(item.getId());
                    return new CalendarItem(
                            item.getId(),
                            item.getCategory().getName(),
                            item.getName(),
                            item.getTrackingMode().name(),
                            availability == null ? 0 : availability.total(),
                            availability == null ? 0 : availability.booked(),
                            availability == null ? 0 : availability.available());
                })
                .toList();
        return new CalendarView(startsAt, endsAt, calendarItems, days(startsAt, endsAt));
    }

    private List<CalendarDay> days(Instant startsAt, Instant endsAt) {
        LocalDate startDate = LocalDate.ofInstant(startsAt, zoneId);
        LocalDate endDate = LocalDate.ofInstant(endsAt.minusMillis(1), zoneId);
        return startDate.datesUntil(endDate.plusDays(1))
                .map(date -> new CalendarDay(
                        date,
                        date.atStartOfDay(zoneId).toInstant(),
                        date.plusDays(1).atStartOfDay(zoneId).toInstant()))
                .toList();
    }

    public record CalendarView(
            Instant startsAt,
            Instant endsAt,
            List<CalendarItem> items,
            List<CalendarDay> days) {
    }

    public record CalendarItem(
            UUID equipmentItemId,
            String categoryName,
            String equipmentName,
            String trackingMode,
            int total,
            int booked,
            int available) {
    }

    public record CalendarDay(LocalDate date, Instant startsAt, Instant endsAt) {
    }
}
