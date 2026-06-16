package oro.tirotiro.equipmentwarehouse.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import oro.tirotiro.equipmentwarehouse.audit.AuditService;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.booking.BookingService.BookingLineCommand;
import oro.tirotiro.equipmentwarehouse.booking.BookingService.CreateBookingCommand;
import oro.tirotiro.equipmentwarehouse.booking.persistence.Booking;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingRepository;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingStatus;
import oro.tirotiro.equipmentwarehouse.config.AppProperties;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategory;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItem;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.TrackingMode;
import oro.tirotiro.equipmentwarehouse.permission.PermissionService;

class BookingServiceTests {

    private static final Instant START = Instant.parse("2026-06-15T09:00:00Z");
    private static final Instant END = Instant.parse("2026-06-15T10:00:00Z");
    private static final Sort STARTS_AT_DESC = Sort.by(Sort.Direction.DESC, "startsAt");
    private static final Sort STARTS_AT_ASC = Sort.by(Sort.Direction.ASC, "startsAt");

    @Test
    void visibleBookingsUsesFilteredQueryForAdmin() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        BookingService bookingService = bookingService(bookingRepository, permissionService);
        User actor = user();
        Booking booking = new Booking(actor, START, END, BookingStatus.BOOKED);
        when(permissionService.isAdmin(actor)).thenReturn(true);
        when(bookingRepository.findAll(any(Specification.class), eq(STARTS_AT_DESC)))
                .thenReturn(List.of(booking));

        assertThat(bookingService.visibleBookings(actor)).containsExactly(booking);

        verify(bookingRepository).findAll(any(Specification.class), eq(STARTS_AT_DESC));
    }

    @Test
    void findBookingsScopesNonAdminToOwnUserId() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingService bookingService = bookingService(bookingRepository);
        User actor = user();
        UUID otherUserId = UUID.randomUUID();
        BookingFilter filter = BookingFilter.of(BookingStatus.BOOKED, otherUserId, null, null, null);
        when(bookingRepository.findAll(any(Specification.class), eq(STARTS_AT_DESC)))
                .thenReturn(List.of());

        bookingService.findBookings(actor, filter);

        verify(bookingRepository).findAll(any(Specification.class), eq(STARTS_AT_DESC));
    }

    @Test
    void findBookingsAppliesAdminUserFilter() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        BookingService bookingService = bookingService(bookingRepository, permissionService);
        User actor = user();
        UUID targetUserId = UUID.randomUUID();
        when(permissionService.isAdmin(actor)).thenReturn(true);
        when(bookingRepository.findAll(any(Specification.class), eq(STARTS_AT_DESC)))
                .thenReturn(List.of());

        bookingService.findBookings(actor, BookingFilter.of(null, targetUserId, null, null, null));

        verify(bookingRepository).findAll(any(Specification.class), eq(STARTS_AT_DESC));
    }

    @Test
    void findBookingsAppliesDateRangeFilter() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingService bookingService = bookingService(bookingRepository);
        User actor = user();
        LocalDate fromDate = LocalDate.parse("2026-06-01");
        LocalDate toDate = LocalDate.parse("2026-06-30");
        when(bookingRepository.findAll(any(Specification.class), eq(STARTS_AT_DESC)))
                .thenReturn(List.of());

        bookingService.findBookings(actor, BookingFilter.of(null, null, fromDate, toDate, null));

        verify(bookingRepository).findAll(any(Specification.class), eq(STARTS_AT_DESC));
    }

    @Test
    void findOverlappingBookingsUsesAscendingSort() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingService bookingService = bookingService(bookingRepository);
        User actor = user();
        when(bookingRepository.findAll(any(Specification.class), eq(STARTS_AT_ASC)))
                .thenReturn(List.of());

        bookingService.findOverlappingBookings(actor, BookingFilter.EMPTY, START, END);

        verify(bookingRepository).findAll(any(Specification.class), eq(STARTS_AT_ASC));
    }

    @Test
    void creationLocksEquipmentItemsInStableSortedOrder() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        EquipmentItemRepository itemRepository = mock(EquipmentItemRepository.class);
        EquipmentUnitRepository unitRepository = mock(EquipmentUnitRepository.class);
        AvailabilityService availabilityService = mock(AvailabilityService.class);
        BookingService bookingService = bookingService(
                bookingRepository,
                mock(PermissionService.class),
                itemRepository,
                unitRepository,
                availabilityService);

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

    @Test
    void deleteBookingRequiresOwnerOrAdminAndReason() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        BookingService bookingService = bookingService(bookingRepository, permissionService);
        User actor = user();
        User other = user();
        UUID bookingId = UUID.randomUUID();
        Booking booking = new Booking(other, START, END, BookingStatus.BOOKED);
        ReflectionTestUtils.setField(booking, "id", bookingId);

        when(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking));
        when(permissionService.isAdmin(actor)).thenReturn(false);
        assertThatThrownBy(() -> bookingService.deleteBooking(bookingId, "duplicate", actor))
                .isInstanceOf(AccessDeniedException.class);

        when(permissionService.isAdmin(actor)).thenReturn(true);
        assertThatThrownBy(() -> bookingService.deleteBooking(bookingId, " ", actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("причину удаления");

        verify(bookingRepository, never()).delete(any(Booking.class));
    }

    @Test
    void deleteBookingAllowsOwner() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        BookingService bookingService = bookingService(bookingRepository, permissionService);
        User actor = user();
        UUID bookingId = UUID.randomUUID();
        Booking booking = new Booking(actor, START, END, BookingStatus.BOOKED);
        ReflectionTestUtils.setField(booking, "id", bookingId);

        when(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking));
        when(permissionService.isAdmin(actor)).thenReturn(false);

        bookingService.deleteBooking(bookingId, "mistake", actor);

        verify(bookingRepository).delete(booking);
    }

    @Test
    void deleteBookingRecordsAuditAndRemovesBooking() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        BookingService bookingService = bookingService(bookingRepository, permissionService);
        User actor = user();
        UUID bookingId = UUID.randomUUID();
        Booking booking = new Booking(actor, START, END, BookingStatus.CANCELLED);
        ReflectionTestUtils.setField(booking, "id", bookingId);

        when(permissionService.isAdmin(actor)).thenReturn(true);
        when(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking));

        bookingService.deleteBooking(bookingId, "test data", actor);

        verify(auditService(bookingService)).record(eq(actor), eq("BOOKING_DELETED"), eq("BOOKING"), eq(bookingId), any());
        verify(bookingRepository).delete(booking);
    }

    @Test
    void deleteAllBookingsForUserRecordsAuditAndRemovesEachBooking() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingService bookingService = bookingService(bookingRepository);
        User actor = user();
        User target = user();
        UUID firstBookingId = UUID.randomUUID();
        UUID secondBookingId = UUID.randomUUID();
        Booking firstBooking = new Booking(target, START, END, BookingStatus.BOOKED);
        ReflectionTestUtils.setField(firstBooking, "id", firstBookingId);
        Booking secondBooking = new Booking(target, END, END.plusSeconds(3600), BookingStatus.CANCELLED);
        ReflectionTestUtils.setField(secondBooking, "id", secondBookingId);
        when(bookingRepository.findByUser_IdOrderByStartsAtDesc(target.getId()))
                .thenReturn(List.of(firstBooking, secondBooking));

        bookingService.deleteAllBookingsForUser(target.getId(), actor);

        verify(auditService(bookingService)).record(eq(actor), eq("BOOKING_DELETED"), eq("BOOKING"), eq(firstBookingId), any());
        verify(auditService(bookingService)).record(eq(actor), eq("BOOKING_DELETED"), eq("BOOKING"), eq(secondBookingId), any());
        verify(bookingRepository).delete(firstBooking);
        verify(bookingRepository).delete(secondBooking);
    }

    private AuditService auditService(BookingService bookingService) {
        return (AuditService) ReflectionTestUtils.getField(bookingService, "auditService");
    }

    private BookingService bookingService(BookingRepository bookingRepository) {
        return bookingService(bookingRepository, mock(PermissionService.class));
    }

    private BookingService bookingService(BookingRepository bookingRepository, PermissionService permissionService) {
        return bookingService(
                bookingRepository,
                permissionService,
                mock(EquipmentItemRepository.class),
                mock(EquipmentUnitRepository.class),
                mock(AvailabilityService.class));
    }

    private BookingService bookingService(
            BookingRepository bookingRepository,
            PermissionService permissionService,
            EquipmentItemRepository itemRepository,
            EquipmentUnitRepository unitRepository,
            AvailabilityService availabilityService) {
        return new BookingService(
                bookingRepository,
                itemRepository,
                unitRepository,
                availabilityService,
                permissionService,
                mock(AuditService.class),
                Clock.fixed(START, ZoneOffset.UTC),
                appProperties());
    }

    private AppProperties appProperties() {
        return new AppProperties(
                "0.2.0-test",
                ZoneId.of("UTC"),
                new AppProperties.Security(false),
                new AppProperties.BootstrapAdmin(null, null, null));
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
