package oro.tirotiro.equipmentwarehouse.booking.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingLineRepository extends JpaRepository<BookingLine, UUID> {

    List<BookingLine> findByBooking_Id(UUID bookingId);
}
