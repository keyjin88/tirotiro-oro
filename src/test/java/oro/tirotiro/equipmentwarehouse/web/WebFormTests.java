package oro.tirotiro.equipmentwarehouse.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.DataBinder;

import oro.tirotiro.equipmentwarehouse.booking.BookingService;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitStatus;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.TrackingMode;

class WebFormTests {

    @Test
    void categoryFormBuildsCreateCommand() {
        CategoryForm form = new CategoryForm();
        form.setName("Cameras");
        form.setDescription("Camera gear");

        assertThat(form.getName()).isEqualTo("Cameras");
        assertThat(form.getDescription()).isEqualTo("Camera gear");
        assertThat(form.toCommand().name()).isEqualTo("Cameras");
        assertThat(form.toCommand().description()).isEqualTo("Camera gear");
    }

    @Test
    void equipmentFormBuildsCreateCommand() {
        UUID categoryId = UUID.randomUUID();
        EquipmentForm form = new EquipmentForm();
        form.setCategoryId(categoryId);
        form.setName("Light");
        form.setManufacturer("Aputure");
        form.setModel("600D");
        form.setDescription("Key light");
        form.setTrackingMode(TrackingMode.UNIT);
        form.setTotalQuantity(0);

        assertThat(form.getCategoryId()).isEqualTo(categoryId);
        assertThat(form.getName()).isEqualTo("Light");
        assertThat(form.getManufacturer()).isEqualTo("Aputure");
        assertThat(form.getModel()).isEqualTo("600D");
        assertThat(form.getDescription()).isEqualTo("Key light");
        assertThat(form.getTrackingMode()).isEqualTo(TrackingMode.UNIT);
        assertThat(form.getTotalQuantity()).isZero();
        assertThat(form.toCommand().categoryId()).isEqualTo(categoryId);
        assertThat(form.toCommand().trackingMode()).isEqualTo(TrackingMode.UNIT);
    }

    @Test
    void unitFormBuildsCreateCommand() {
        UnitForm form = new UnitForm();
        assertThat(form.getCondition()).isEqualTo("Готово");

        form.setInventoryNumber("INV-1");
        form.setSerialNumber("SER-1");
        form.setCondition("Ready");
        form.setStatus(EquipmentUnitStatus.MAINTENANCE);
        form.setNotes("Needs calibration");

        assertThat(form.getInventoryNumber()).isEqualTo("INV-1");
        assertThat(form.getSerialNumber()).isEqualTo("SER-1");
        assertThat(form.getCondition()).isEqualTo("Ready");
        assertThat(form.getStatus()).isEqualTo(EquipmentUnitStatus.MAINTENANCE);
        assertThat(form.getNotes()).isEqualTo("Needs calibration");
        assertThat(form.toCommand().inventoryNumber()).isEqualTo("INV-1");
        assertThat(form.toCommand().status()).isEqualTo(EquipmentUnitStatus.MAINTENANCE);
    }

    @Test
    void bookingFormBuildsZonedCreateCommand() {
        UUID itemId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        BookingForm form = new BookingForm();
        form.setStartsAt(LocalDateTime.parse("2026-06-15T12:00:00"));
        form.setEndsAt(LocalDateTime.parse("2026-06-15T13:30:00"));
        form.setComment("Shoot");
        form.setEquipmentItemId(itemId);
        form.setEquipmentUnitId(unitId);
        form.setQuantity(1);

        assertThat(form.getStartsAt()).isEqualTo(LocalDateTime.parse("2026-06-15T12:00:00"));
        assertThat(form.getEndsAt()).isEqualTo(LocalDateTime.parse("2026-06-15T13:30:00"));
        assertThat(form.getComment()).isEqualTo("Shoot");
        assertThat(form.getEquipmentItemId()).isEqualTo(itemId);
        assertThat(form.getEquipmentUnitId()).isEqualTo(unitId);
        assertThat(form.getQuantity()).isEqualTo(1);
        assertThat(form.toCommand(ZoneId.of("UTC")).startsAt().toString()).isEqualTo("2026-06-15T12:00:00Z");
        assertThat(form.toCommand(ZoneId.of("UTC")).lines()).singleElement()
                .satisfies(line -> {
                    assertThat(line.equipmentItemId()).isEqualTo(itemId);
                    assertThat(line.equipmentUnitId()).isEqualTo(unitId);
                    assertThat(line.quantity()).isEqualTo(1);
                });
    }

    @Test
    void bookingFormBindsMultipleLinesFromIndexedFields() {
        UUID quantityItemId = UUID.randomUUID();
        UUID unitItemId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        BookingForm form = new BookingForm();
        MutablePropertyValues values = new MutablePropertyValues();
        values.add("startsAt", "2026-06-15T12:00:00");
        values.add("endsAt", "2026-06-15T13:30:00");
        values.add("lines[0].equipmentItemId", quantityItemId.toString());
        values.add("lines[0].quantity", "3");
        values.add("lines[1].equipmentItemId", unitItemId.toString());
        values.add("lines[1].equipmentUnitId", unitId.toString());
        values.add("lines[1].quantity", "1");

        new DataBinder(form, "form").bind(values);

        assertThat(form.toCommand(ZoneId.of("UTC")).lines())
                .extracting(
                        BookingService.BookingLineCommand::equipmentItemId,
                        BookingService.BookingLineCommand::equipmentUnitId,
                        BookingService.BookingLineCommand::quantity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(quantityItemId, null, 3),
                        org.assertj.core.groups.Tuple.tuple(unitItemId, unitId, 1));
    }

    @Test
    void bookingFormRemovesMiddleLineAndCompactsRemainingLines() {
        UUID firstItemId = UUID.randomUUID();
        UUID removedItemId = UUID.randomUUID();
        UUID lastItemId = UUID.randomUUID();
        UUID lastUnitId = UUID.randomUUID();
        BookingForm form = new BookingForm();
        MutablePropertyValues values = new MutablePropertyValues();
        values.add("startsAt", "2026-06-15T12:00:00");
        values.add("endsAt", "2026-06-15T13:30:00");
        values.add("lines[0].equipmentItemId", firstItemId.toString());
        values.add("lines[0].quantity", "2");
        values.add("lines[1].equipmentItemId", removedItemId.toString());
        values.add("lines[1].quantity", "4");
        values.add("lines[2].equipmentItemId", lastItemId.toString());
        values.add("lines[2].equipmentUnitId", lastUnitId.toString());
        values.add("lines[2].quantity", "1");

        new DataBinder(form, "form").bind(values);
        form.removeLine(1);

        assertThat(form.toCommand(ZoneId.of("UTC")).lines())
                .extracting(
                        BookingService.BookingLineCommand::equipmentItemId,
                        BookingService.BookingLineCommand::equipmentUnitId,
                        BookingService.BookingLineCommand::quantity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(firstItemId, null, 2),
                        org.assertj.core.groups.Tuple.tuple(lastItemId, lastUnitId, 1));
    }

    @Test
    void registrationFormBuildsRegistrationCommandAndChecksPasswordConfirmation() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail("viewer@example.com");
        form.setDisplayName("Viewer");
        form.setPassword("password123");
        form.setPasswordConfirmation("password123");

        assertThat(form.passwordsMatch()).isTrue();
        assertThat(form.toCommand().email()).isEqualTo("viewer@example.com");
        assertThat(form.toCommand().displayName()).isEqualTo("Viewer");
        assertThat(form.toCommand().password()).isEqualTo("password123");
    }

    @Test
    void cancelBookingFormStoresReason() {
        CancelBookingForm form = new CancelBookingForm();
        form.setReason("Changed plan");

        assertThat(form.getReason()).isEqualTo("Changed plan");
    }
}
