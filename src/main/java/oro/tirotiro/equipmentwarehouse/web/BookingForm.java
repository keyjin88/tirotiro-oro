package oro.tirotiro.equipmentwarehouse.web;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

    @Size(min = 1)
    private List<@Valid LineForm> lines = new ArrayList<>(List.of(new LineForm()));

    public BookingService.CreateBookingCommand toCommand(ZoneId zoneId) {
        return new BookingService.CreateBookingCommand(
                toInstant(startsAt, zoneId),
                toInstant(endsAt, zoneId),
                comment,
                lines.stream()
                        .map(line -> new BookingService.BookingLineCommand(
                                line.getEquipmentItemId(),
                                line.getEquipmentUnitId(),
                                line.getQuantity()))
                        .toList());
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
        return firstLine().getEquipmentItemId();
    }

    public void setEquipmentItemId(UUID equipmentItemId) {
        firstLine().setEquipmentItemId(equipmentItemId);
    }

    public UUID getEquipmentUnitId() {
        return firstLine().getEquipmentUnitId();
    }

    public void setEquipmentUnitId(UUID equipmentUnitId) {
        firstLine().setEquipmentUnitId(equipmentUnitId);
    }

    public int getQuantity() {
        return firstLine().getQuantity();
    }

    public void setQuantity(int quantity) {
        firstLine().setQuantity(quantity);
    }

    public List<LineForm> getLines() {
        return lines;
    }

    public void setLines(List<LineForm> lines) {
        this.lines = lines == null ? new ArrayList<>() : lines;
    }

    public void ensureAtLeastOneLine() {
        if (lines == null) {
            lines = new ArrayList<>();
        }
        if (lines.isEmpty()) {
            lines.add(new LineForm());
        }
    }

    public void removeLine(int index) {
        if (lines == null) {
            lines = new ArrayList<>();
        }
        if (index >= 0 && index < lines.size()) {
            lines.remove(index);
        }
        ensureAtLeastOneLine();
    }

    private LineForm firstLine() {
        ensureAtLeastOneLine();
        return lines.getFirst();
    }

    public static class LineForm {

        @NotNull
        private UUID equipmentItemId;

        private UUID equipmentUnitId;

        @Min(1)
        private int quantity = 1;

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
}
