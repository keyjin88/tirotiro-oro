package oro.tirotiro.equipmentwarehouse.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import oro.tirotiro.equipmentwarehouse.audit.AuditService;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.booking.BookingService.BookingLineCommand;
import oro.tirotiro.equipmentwarehouse.booking.BookingService.CreateBookingCommand;
import oro.tirotiro.equipmentwarehouse.booking.persistence.Booking;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingRepository;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingStatus;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategory;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItem;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.TrackingMode;
import oro.tirotiro.equipmentwarehouse.permission.PermissionService;

class BookingServiceTests {

    private static final Instant START = Instant.parse("2026-06-15T09:00:00Z");
    private static final Instant END = Instant.parse("2026-06-15T10:00:00Z");

    @Test
    void visibleBookingsUsesAdminPermissionPath() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        EquipmentItemRepository itemRepository = mock(EquipmentItemRepository.class);
        EquipmentUnitRepository unitRepository = mock(EquipmentUnitRepository.class);
        AvailabilityService availabilityService = mock(AvailabilityService.class);
        PermissionService permissionService = mock(PermissionService.class);
        AuditService auditService = mock(AuditService.class);
        BookingService bookingService = new BookingService(
                bookingRepository,
                itemRepository,
                unitRepository,
                availabilityService,
                permissionService,
                auditService,
                Clock.fixed(START, ZoneOffset.UTC));
        User actor = user();
        Booking booking = new Booking(actor, START, END, BookingStatus.BOOKED);
        when(permissionService.isAdmin(actor)).thenReturn(true);
        when(bookingRepository.findAllByOrderByStartsAtDesc()).thenReturn(List.of(booking));

        assertThat(bookingService.visibleBookings(actor)).containsExactly(booking);

        verify(permissionService).isAdmin(actor);
        verify(bookingRepository).findAllByOrderByStartsAtDesc();
        verify(bookingRepository, never()).findByUser_IdOrderByStartsAtDesc(actor.getId());
    }

    @Test
    void creationLocksEquipmentItemsInStableSortedOrder() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        EquipmentItemRepository itemRepository = mock(EquipmentItemRepository.class);
        EquipmentUnitRepository unitRepository = mock(EquipmentUnitRepository.class);
        AvailabilityService availabilityService = mock(AvailabilityService.class);
        PermissionService permissionService = mock(PermissionService.class);
        AuditService auditService = mock(AuditService.class);
        BookingService bookingService = new BookingService(
                bookingRepository,
                itemRepository,
                unitRepository,
                availabilityService,
                permissionService,
                auditService,
                Clock.fixed(START, ZoneOffset.UTC));

        UUID lowId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID highId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        EquipmentItem highItem = item(highId);
        EquipmentItem lowItem = item(lowId);
        User actor = user();
        CreateBookingCommand command = new CreateBookingCommand(
                START,
                END,
                "shoot",
                List.of(
                        new BookingLineCommand(highId, null, 1),
                        new BookingLineCommand(lowId, null, 1)));

        when(itemRepository.lockByIdInOrderById(List.of(lowId, highId))).thenReturn(List.of(lowItem, highItem));
        when(availabilityService.blockingStatuses()).thenReturn(Set.of(BookingStatus.BOOKED));
        when(bookingRepository.findOverlappingForItems(eq(List.of(lowId, highId)), eq(START), eq(END), any()))
                .thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
            return booking;
        });

        bookingService.createBooking(command, actor);

        verify(itemRepository).lockByIdInOrderById(List.of(lowId, highId));
        verify(availabilityService).assertAvailable(
                eq(List.of(lowItem, highItem)),
                eq(List.of()),
                eq(List.of(
                        new AvailabilityService.RequestedLine(highId, null, 1),
                        new AvailabilityService.RequestedLine(lowId, null, 1))),
                eq(START),
                eq(END),
                eq(List.of()),
                eq(null));
    }

    private EquipmentItem item(UUID id) {
        EquipmentCategory category = new EquipmentCategory("Category", null);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        EquipmentItem item = new EquipmentItem(category, "Item " + id, TrackingMode.QUANTITY, 3);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private User user() {
        User user = new User("user@example.com", "hash", "User");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
