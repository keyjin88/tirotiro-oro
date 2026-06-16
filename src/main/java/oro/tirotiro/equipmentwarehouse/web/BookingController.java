package oro.tirotiro.equipmentwarehouse.web;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import oro.tirotiro.equipmentwarehouse.auth.CurrentUserService;
import oro.tirotiro.equipmentwarehouse.booking.AvailabilityException;
import oro.tirotiro.equipmentwarehouse.booking.BookingFilter;
import oro.tirotiro.equipmentwarehouse.booking.BookingService;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingStatus;
import oro.tirotiro.equipmentwarehouse.config.AppProperties;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItem;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnit;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitRepository;

@Controller
@RequestMapping("/bookings")
public class BookingController {

    private static final int EQUIPMENT_SEARCH_LIMIT = 20;
    private static final int MIN_SEARCH_LENGTH = 2;

    private final BookingService bookingService;
    private final EquipmentItemRepository itemRepository;
    private final EquipmentUnitRepository unitRepository;
    private final CurrentUserService currentUserService;
    private final BookingFilterSupport bookingFilterSupport;
    private final AppProperties appProperties;
    private final Clock clock;

    public BookingController(
            BookingService bookingService,
            EquipmentItemRepository itemRepository,
            EquipmentUnitRepository unitRepository,
            CurrentUserService currentUserService,
            BookingFilterSupport bookingFilterSupport,
            AppProperties appProperties,
            Clock clock) {
        this.bookingService = bookingService;
        this.itemRepository = itemRepository;
        this.unitRepository = unitRepository;
        this.currentUserService = currentUserService;
        this.bookingFilterSupport = bookingFilterSupport;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID equipmentItemId,
            Model model) {
        BookingFilter filter = bookingFilterSupport.parse(status, userId, fromDate, toDate, equipmentItemId);
        addBookings(model, filter);
        return "bookings/list";
    }

    @GetMapping("/table")
    public String table(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID equipmentItemId,
            Model model) {
        BookingFilter filter = bookingFilterSupport.parse(status, userId, fromDate, toDate, equipmentItemId);
        addBookings(model, filter);
        return "bookings/partials/table :: bookingsTable";
    }

    @GetMapping("/new")
    public String newBooking(Model model) {
        addFormModel(model, defaultFormForDate(null));
        return "bookings/new";
    }

    @GetMapping("/modal")
    public String bookingModal(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String returnUrl,
            Model model) {
        addFormModel(model, defaultFormForDate(date));
        model.addAttribute("returnUrl", sanitizeReturnUrl(returnUrl));
        return "bookings/partials/modal :: bookingModal";
    }

    @GetMapping("/line")
    public String bookingLine(@RequestParam(defaultValue = "0") int index, Model model) {
        addLineModel(model, index, new BookingForm.LineForm(), null, List.of());
        return "bookings/partials/line :: bookingLine";
    }

    @PostMapping("/line/remove")
    public String removeBookingLine(
            @ModelAttribute("form") BookingForm form,
            @RequestParam int removeIndex,
            Model model) {
        form.removeLine(removeIndex);
        addFormModel(model, form);
        return "bookings/partials/form-fields :: bookingLines";
    }

    @GetMapping("/equipment-search")
    public String equipmentSearch(
            @RequestParam int lineIndex,
            @RequestParam(defaultValue = "") String q,
            Model model) {
        String query = q == null ? "" : q.trim();
        List<EquipmentItem> items = query.length() < MIN_SEARCH_LENGTH
                ? List.of()
                : itemRepository.searchActiveForBooking(query, PageRequest.of(0, EQUIPMENT_SEARCH_LIMIT));
        model.addAttribute("lineIndex", lineIndex);
        model.addAttribute("query", query);
        model.addAttribute("items", items);
        model.addAttribute("minSearchLength", MIN_SEARCH_LENGTH);
        return "bookings/partials/search-results :: searchResults";
    }

    @GetMapping("/equipment-selection")
    public String equipmentSelection(
            @RequestParam int lineIndex,
            @RequestParam UUID equipmentItemId,
            Model model) {
        EquipmentItem selectedItem = itemRepository.findDetailedById(equipmentItemId)
                .filter(EquipmentItem::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Оборудование не найдено: " + equipmentItemId));
        BookingForm.LineForm line = new BookingForm.LineForm();
        line.setEquipmentItemId(selectedItem.getId());
        line.setQuantity(1);
        addLineModel(model, lineIndex, line, selectedItem, unitsFor(selectedItem));
        return "bookings/partials/line :: bookingLine";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("form") BookingForm form,
            BindingResult bindingResult,
            @RequestParam(required = false) String returnUrl,
            @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
            Model model,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request,
            HttpServletResponse response) {
        String redirectTarget = sanitizeReturnUrl(returnUrl);
        if (bindingResult.hasErrors()) {
            addFormModel(model, form);
            if (redirectTarget != null) {
                model.addAttribute("returnUrl", redirectTarget);
                return "bookings/partials/modal :: bookingModal";
            }
            return "bookings/new";
        }
        try {
            bookingService.createBooking(form.toCommand(appProperties.timeZone()), currentUserService.requireCurrentUser());
            String target = redirectTarget == null ? "/bookings" : redirectTarget;
            if ("true".equals(htmxRequest)) {
                var flashMap = RequestContextUtils.getOutputFlashMap(request);
                if (flashMap != null) {
                    flashMap.put("message", "Бронирование создано");
                }
                response.setHeader("HX-Redirect", target);
                return "fragments/empty :: empty";
            }
            redirectAttributes.addFlashAttribute("message", "Бронирование создано");
            return "redirect:" + target;
        } catch (AvailabilityException | IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            addFormModel(model, form);
            if (redirectTarget != null) {
                model.addAttribute("returnUrl", redirectTarget);
                return "bookings/partials/modal :: bookingModal";
            }
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
            redirectAttributes.addFlashAttribute("message", "Бронирование отменено");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/bookings";
    }

    @PostMapping("/{bookingId}/delete")
    public String delete(
            @PathVariable UUID bookingId,
            @RequestParam String reason,
            @RequestParam(required = false) String returnUrl,
            RedirectAttributes redirectAttributes) {
        try {
            bookingService.deleteBooking(bookingId, reason, currentUserService.requireCurrentUser());
            redirectAttributes.addFlashAttribute("message", "Бронирование удалено");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        String target = sanitizeReturnUrl(returnUrl);
        return "redirect:" + (target == null ? "/bookings" : target);
    }

    private void addBookings(Model model, BookingFilter filter) {
        var actor = currentUserService.requireCurrentUser();
        model.addAttribute("bookings", bookingService.findBookings(actor, filter));
        model.addAttribute("cancelForm", new CancelBookingForm());
        bookingFilterSupport.addFilterModel(model, actor, filter, false, null);
    }

    private void addFormModel(Model model, BookingForm form) {
        form.ensureAtLeastOneLine();
        List<UUID> selectedItemIds = form.getLines().stream()
                .map(BookingForm.LineForm::getEquipmentItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, EquipmentItem> selectedItemsById = selectedItemIds.stream()
                .map(id -> itemRepository.findDetailedById(id).filter(EquipmentItem::isActive).orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(EquipmentItem::getName))
                .collect(Collectors.toMap(EquipmentItem::getId, Function.identity(), (first, second) -> first));
        Map<UUID, List<EquipmentUnit>> unitsByItemId = selectedItemsById.values().stream()
                .collect(Collectors.toMap(EquipmentItem::getId, this::unitsFor));
        model.addAttribute("form", form);
        model.addAttribute("selectedItemsById", selectedItemsById);
        model.addAttribute("unitsByItemId", unitsByItemId);
        model.addAttribute("minSearchLength", MIN_SEARCH_LENGTH);
    }

    private void addLineModel(
            Model model,
            int lineIndex,
            BookingForm.LineForm line,
            EquipmentItem selectedItem,
            List<EquipmentUnit> units) {
        BookingForm form = new BookingForm();
        List<BookingForm.LineForm> lines = new ArrayList<>();
        for (int i = 0; i <= lineIndex; i++) {
            lines.add(new BookingForm.LineForm());
        }
        lines.set(lineIndex, line);
        form.setLines(lines);
        model.addAttribute("form", form);
        model.addAttribute("lineIndex", lineIndex);
        model.addAttribute("line", line);
        model.addAttribute("selectedItem", selectedItem);
        model.addAttribute("units", units);
        model.addAttribute("minSearchLength", MIN_SEARCH_LENGTH);
    }

    private List<EquipmentUnit> unitsFor(EquipmentItem item) {
        return unitRepository.findByEquipmentItem_IdAndArchivedFalseOrderByInventoryNumberAsc(item.getId());
    }

    private BookingForm defaultFormForDate(LocalDate date) {
        BookingForm form = new BookingForm();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), appProperties.timeZone());
        LocalDateTime start;
        if (date != null) {
            start = date.equals(now.toLocalDate())
                    ? now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
                    : date.atTime(9, 0);
            form.setStartsAt(start);
            form.setEndsAt(date.atTime(23, 59));
        } else {
            start = now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
            form.setStartsAt(start);
            form.setEndsAt(start.plusHours(2));
        }
        return form;
    }

    private String sanitizeReturnUrl(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()) {
            return null;
        }
        if (!returnUrl.startsWith("/") || returnUrl.startsWith("//")) {
            return null;
        }
        return returnUrl;
    }
}
