package oro.tirotiro.equipmentwarehouse.web;

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
import oro.tirotiro.equipmentwarehouse.auth.UserAdministrationService;
import oro.tirotiro.equipmentwarehouse.booking.BookingService;
import oro.tirotiro.equipmentwarehouse.auth.persistence.RoleCode;
import oro.tirotiro.equipmentwarehouse.auth.persistence.UserRepository;
import oro.tirotiro.equipmentwarehouse.inventory.InventoryService;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategoryRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitStatus;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.TrackingMode;
import oro.tirotiro.equipmentwarehouse.permission.PermissionService;
import oro.tirotiro.equipmentwarehouse.permission.persistence.PermissionCode;
import oro.tirotiro.equipmentwarehouse.permission.persistence.PermissionRepository;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final InventoryService inventoryService;
    private final BookingService bookingService;
    private final PermissionService permissionService;
    private final UserAdministrationService userAdministrationService;
    private final CurrentUserService currentUserService;
    private final EquipmentCategoryRepository categoryRepository;
    private final EquipmentItemRepository itemRepository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;

    public AdminController(
            InventoryService inventoryService,
            BookingService bookingService,
            PermissionService permissionService,
            UserAdministrationService userAdministrationService,
            CurrentUserService currentUserService,
            EquipmentCategoryRepository categoryRepository,
            EquipmentItemRepository itemRepository,
            UserRepository userRepository,
            PermissionRepository permissionRepository) {
        this.inventoryService = inventoryService;
        this.bookingService = bookingService;
        this.permissionService = permissionService;
        this.userAdministrationService = userAdministrationService;
        this.currentUserService = currentUserService;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
    }

    @GetMapping
    public String dashboard() {
        return "redirect:/admin/equipment";
    }

    @GetMapping("/equipment")
    public String equipment(Model model) {
        addEquipmentAdminModel(model, new CategoryForm(), new EquipmentForm(), new UnitForm());
        return "admin/equipment";
    }

    @PostMapping("/categories")
    public String createCategory(
            @Valid @ModelAttribute("categoryForm") CategoryForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addEquipmentAdminModel(model, form, new EquipmentForm(), new UnitForm());
            return "admin/equipment";
        }
        try {
            inventoryService.createCategory(form.toCommand(), currentUserService.requireCurrentUser());
            redirectAttributes.addFlashAttribute("message", "Категория создана");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/equipment";
    }

    @PostMapping("/equipment")
    public String createEquipment(
            @Valid @ModelAttribute("equipmentForm") EquipmentForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addEquipmentAdminModel(model, new CategoryForm(), form, new UnitForm());
            return "admin/equipment";
        }
        try {
            inventoryService.createItem(form.toCommand(), currentUserService.requireCurrentUser());
            redirectAttributes.addFlashAttribute("message", "Оборудование создано");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/equipment";
    }

    @PostMapping("/equipment/{itemId}/units")
    public String createUnit(
            @PathVariable UUID itemId,
            @Valid @ModelAttribute("unitForm") UnitForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addEquipmentAdminModel(model, new CategoryForm(), new EquipmentForm(), form);
            return "admin/equipment";
        }
        try {
            inventoryService.createUnit(itemId, form.toCommand(), currentUserService.requireCurrentUser());
            redirectAttributes.addFlashAttribute("message", "Единица создана");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/equipment";
    }

    @PostMapping("/equipment/{itemId}/delete")
    public String deleteEquipment(
            @PathVariable UUID itemId,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {
        try {
            inventoryService.softDeleteItem(itemId, reason, currentUserService.requireCurrentUser());
            redirectAttributes.addFlashAttribute("message", "Оборудование архивировано");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/equipment";
    }

    @PostMapping("/bookings/{bookingId}/delete")
    public String deleteBooking(
            @PathVariable UUID bookingId,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {
        try {
            bookingService.deleteBooking(bookingId, reason, currentUserService.requireCurrentUser());
            redirectAttributes.addFlashAttribute("message", "Бронирование удалено");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/bookings";
    }

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userRepository.findAllWithRolesAndPermissions());
        model.addAttribute("roleCodes", RoleCode.values());
        model.addAttribute("permissions", permissionRepository.findAll());
        model.addAttribute("permissionCodes", PermissionCode.values());
        return "admin/users";
    }

    @PostMapping("/users/{userId}/roles")
    public String updateRole(
            @PathVariable UUID userId,
            @RequestParam RoleCode roleCode,
            @RequestParam String action,
            RedirectAttributes redirectAttributes) {
        try {
            if ("grant".equals(action)) {
                userAdministrationService.grantRole(userId, roleCode, currentUserService.requireCurrentUser());
                redirectAttributes.addFlashAttribute("message", "Роль выдана");
            } else if ("revoke".equals(action)) {
                userAdministrationService.revokeRole(userId, roleCode, currentUserService.requireCurrentUser());
                redirectAttributes.addFlashAttribute("message", "Роль отозвана");
            }
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{userId}/permissions")
    public String updatePermission(
            @PathVariable UUID userId,
            @RequestParam PermissionCode permissionCode,
            @RequestParam String action,
            RedirectAttributes redirectAttributes) {
        try {
            if ("grant".equals(action)) {
                permissionService.grantPermission(userId, permissionCode, currentUserService.requireCurrentUser());
                redirectAttributes.addFlashAttribute("message", "Право выдано");
            } else if ("revoke".equals(action)) {
                permissionService.revokePermission(userId, permissionCode, currentUserService.requireCurrentUser());
                redirectAttributes.addFlashAttribute("message", "Право отозвано");
            }
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{userId}/delete")
    public String deleteUser(@PathVariable UUID userId, RedirectAttributes redirectAttributes) {
        try {
            userAdministrationService.deleteUser(userId, currentUserService.requireCurrentUser());
            redirectAttributes.addFlashAttribute("message", "Пользователь удалён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    private void addEquipmentAdminModel(Model model, CategoryForm categoryForm, EquipmentForm equipmentForm, UnitForm unitForm) {
        model.addAttribute("categoryForm", categoryForm);
        model.addAttribute("equipmentForm", equipmentForm);
        model.addAttribute("unitForm", unitForm);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("items", itemRepository.findAllDetailed());
        model.addAttribute("trackingModes", TrackingMode.values());
        model.addAttribute("unitStatuses", EquipmentUnitStatus.values());
    }
}
