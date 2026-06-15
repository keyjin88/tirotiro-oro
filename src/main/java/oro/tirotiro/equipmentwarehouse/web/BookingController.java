package oro.tirotiro.equipmentwarehouse.web;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import oro.tirotiro.equipmentwarehouse.auth.CurrentUserService;
import oro.tirotiro.equipmentwarehouse.booking.AvailabilityException;
import oro.tirotiro.equipmentwarehouse.booking.BookingService;
import oro.tirotiro.equipmentwarehouse.config.AppProperties;
import oro.tirotiro.equipmentwarehouse.inventory.InventoryService;

@Controller
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final InventoryService inventoryService;
    private final CurrentUserService currentUserService;
    private final AppProperties appProperties;
    private final Clock clock;

    public BookingController(
            BookingService bookingService,
            InventoryService inventoryService,
            CurrentUserService currentUserService,
            AppProperties appProperties,
            Clock clock) {
        this.bookingService = bookingService;
        this.inventoryService = inventoryService;
        this.currentUserService = currentUserService;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    @GetMapping
    public String list(Model model) {
        addBookings(model);
        return "bookings/list";
    }

    @GetMapping("/table")
    public String table(Model model) {
        addBookings(model);
        return "bookings/partials/table :: bookingsTable";
    }

    @GetMapping("/new")
    public String newBooking(Model model) {
        BookingForm form = new BookingForm();
        LocalDateTime start = LocalDateTime.ofInstant(clock.instant(), appProperties.timeZone()).plusHours(1).withMinute(0).withSecond(0).withNano(0);
        form.setStartsAt(start);
        form.setEndsAt(start.plusHours(2));
        addFormModel(model, form);
        return "bookings/new";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("form") BookingForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addFormModel(model, form);
            return "bookings/new";
        }
        try {
            bookingService.createBooking(form.toCommand(appProperties.timeZone()), currentUserService.requireCurrentUser());
            redirectAttributes.addFlashAttribute("message", "Booking created");
            return "redirect:/bookings";
        } catch (AvailabilityException | IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            addFormModel(model, form);
            return "bookings/new";
        }
    }

    @PostMapping("/{bookingId}/cancel")
    public String cancel(
            @PathVariable UUID bookingId,
            @ModelAttribute CancelBookingForm form,
            RedirectAttributes redirectAttributes) {
        try {
            bookingService.cancelBooking(bookingId, form.getReason(), currentUserService.requireCurrentUser());
            redirectAttributes.addFlashAttribute("message", "Booking cancelled");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/bookings";
    }

    private void addBookings(Model model) {
        model.addAttribute("bookings", bookingService.visibleBookings(currentUserService.requireCurrentUser()));
        model.addAttribute("cancelForm", new CancelBookingForm());
    }

    private void addFormModel(Model model, BookingForm form) {
        model.addAttribute("form", form);
        model.addAttribute("items", inventoryService.findActiveCatalog());
    }
}
