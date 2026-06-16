package oro.tirotiro.equipmentwarehouse.web;

import java.time.Instant;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import oro.tirotiro.equipmentwarehouse.auth.CurrentUserService;
import oro.tirotiro.equipmentwarehouse.booking.AvailabilityService;
import oro.tirotiro.equipmentwarehouse.inventory.InventoryService;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategoryRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItem;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.TrackingMode;

@Controller
@RequestMapping("/equipment")
public class EquipmentController {

    private final InventoryService inventoryService;
    private final AvailabilityService availabilityService;
    private final CurrentUserService currentUserService;
    private final EquipmentCategoryRepository categoryRepository;
    private final EquipmentItemRepository itemRepository;
    private final EquipmentUnitRepository unitRepository;

    public EquipmentController(
            InventoryService inventoryService,
            AvailabilityService availabilityService,
            CurrentUserService currentUserService,
            EquipmentCategoryRepository categoryRepository,
            EquipmentItemRepository itemRepository,
            EquipmentUnitRepository unitRepository) {
        this.inventoryService = inventoryService;
        this.availabilityService = availabilityService;
        this.currentUserService = currentUserService;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.unitRepository = unitRepository;
    }

    @GetMapping
    public String catalog(Model model) {
        model.addAttribute("items", inventoryService.findActiveCatalog());
        return "equipment/catalog";
    }

    @GetMapping("/table")
    public String catalogTable(Model model) {
        model.addAttribute("items", inventoryService.findActiveCatalog());
        return "equipment/partials/table :: equipmentTable";
    }

    @GetMapping("/new")
    public String newEquipment(Model model) {
        addCreateModel(model, new EquipmentForm());
        return "equipment/new";
    }

    @PostMapping
    public String createEquipment(
            @Valid @ModelAttribute("form") EquipmentForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addCreateModel(model, form);
            return "equipment/new";
        }
        try {
            EquipmentItem item = inventoryService.createItem(form.toCommand(), currentUserService.requireCurrentUser());
            redirectAttributes.addFlashAttribute("message", "Оборудование создано");
            return "redirect:/equipment/" + item.getId();
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            addCreateModel(model, form);
            return "equipment/new";
        }
    }

    @GetMapping("/{itemId}")
    public String details(@PathVariable UUID itemId, Model model) {
        addDetailsModel(itemId, model);
        return "equipment/details";
    }

    @GetMapping("/{itemId}/details")
    public String detailsPartial(@PathVariable UUID itemId, Model model) {
        addDetailsModel(itemId, model);
        return "equipment/partials/details :: equipmentDetails";
    }

    @GetMapping("/{itemId}/units")
    public String unitsPartial(@PathVariable UUID itemId, Model model) {
        model.addAttribute("units", unitRepository.findByEquipmentItem_IdAndArchivedFalse(itemId));
        return "equipment/partials/units :: unitOptions";
    }

    @GetMapping("/units")
    public String unitsForSelection(@RequestParam UUID equipmentItemId, Model model) {
        model.addAttribute("units", unitRepository.findByEquipmentItem_IdAndArchivedFalse(equipmentItemId));
        return "equipment/partials/units :: unitOptions";
    }

    @GetMapping("/availability")
    public String availabilityPartial(
            @RequestParam UUID equipmentItemId,
            @RequestParam Instant startsAt,
            @RequestParam Instant endsAt,
            Model model) {
        model.addAttribute("availability", availabilityService.getAvailability(List.of(equipmentItemId), startsAt, endsAt)
                .stream()
                .findFirst()
                .orElse(null));
        return "equipment/partials/availability :: availabilitySummary";
    }

    private void addCreateModel(Model model, EquipmentForm form) {
        model.addAttribute("form", form);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("trackingModes", TrackingMode.values());
    }

    private void addDetailsModel(UUID itemId, Model model) {
        EquipmentItem item = itemRepository.findDetailedById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Оборудование не найдено: " + itemId));
        model.addAttribute("item", item);
        model.addAttribute("units", unitRepository.findByEquipmentItem_IdAndArchivedFalse(itemId));
    }
}
