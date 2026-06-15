package oro.tirotiro.equipmentwarehouse.inventory.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentUnitRepository extends JpaRepository<EquipmentUnit, UUID> {

    List<EquipmentUnit> findByEquipmentItem_IdAndArchivedFalse(UUID equipmentItemId);

    Optional<EquipmentUnit> findByInventoryNumber(String inventoryNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select unit from EquipmentUnit unit where unit.id in :ids order by unit.id")
    List<EquipmentUnit> lockByIdInOrderById(@Param("ids") Collection<UUID> ids);
}
