package oro.tirotiro.equipmentwarehouse.web;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.auth.persistence.UserRepository;
import oro.tirotiro.equipmentwarehouse.booking.BookingFilter;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingStatus;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;
import oro.tirotiro.equipmentwarehouse.permission.PermissionService;

@Component
public class BookingFilterSupport {

    private final UserRepository userRepository;
    private final EquipmentItemRepository equipmentItemRepository;
    private final PermissionService permissionService;

    public BookingFilterSupport(
            UserRepository userRepository,
            EquipmentItemRepository equipmentItemRepository,
            PermissionService permissionService) {
        this.userRepository = userRepository;
        this.equipmentItemRepository = equipmentItemRepository;
        this.permissionService = permissionService;
    }

    public BookingFilter parse(
            BookingStatus status,
            UUID userId,
            LocalDate fromDate,
            LocalDate toDate,
            UUID equipmentItemId) {
        return BookingFilter.of(status, userId, fromDate, toDate, equipmentItemId);
    }

    public void addFilterModel(
            Model model,
            User actor,
            BookingFilter filter,
            boolean calendarContext,
            YearMonth month) {
        model.addAttribute("bookingFilter", filter);
        model.addAttribute("filterCalendarContext", calendarContext);
        if (calendarContext && month != null) {
            model.addAttribute("filterMonth", month);
        }
        if (permissionService.isAdmin(actor)) {
            model.addAttribute("filterUsers", userRepository.findAllByOrderByDisplayNameAsc());
        }
        model.addAttribute("filterEquipment", equipmentItemRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("filterStatuses", BookingStatus.values());
    }
}
