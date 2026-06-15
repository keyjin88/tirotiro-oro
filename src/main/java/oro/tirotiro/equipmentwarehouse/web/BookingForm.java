package oro.tirotiro.equipmentwarehouse.web;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;

import oro.tirotiro.equipmentwarehouse.booking.BookingService;

public class BookingForm {

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startsAt;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endsAt;

    private String comment;

    @NotNull
    private UUID equipmentItemId;

    private UUID equipmentUnitId;

    @Min(1)
    private int quantity = 1;

    public BookingService.CreateBookingCommand toCommand(ZoneId zoneId) {
        return new BookingService.CreateBookingCommand(
                toInstant(startsAt, zoneId),
                toInstant(endsAt, zoneId),
                comment,
                List.of(new BookingService.BookingLineCommand(equipmentItemId, equipmentUnitId, quantity)));
    }

    private Instant toInstant(LocalDateTime dateTime, ZoneId zoneId) {
        return dateTime == null ? null : dateTime.atZone(zoneId).toInstant();
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(LocalDateTime startsAt) {
        this.startsAt = startsAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(LocalDateTime endsAt) {
        this.endsAt = endsAt;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public UUID getEquipmentItemId() {
        return equipmentItemId;
    }

    public void setEquipmentItemId(UUID equipmentItemId) {
        this.equipmentItemId = equipmentItemId;
    }

    public UUID getEquipmentUnitId() {
        return equipmentUnitId;
    }

    public void setEquipmentUnitId(UUID equipmentUnitId) {
        this.equipmentUnitId = equipmentUnitId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
