package oro.tirotiro.equipmentwarehouse.inventory.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentItemRepository extends JpaRepository<EquipmentItem, UUID> {

    @EntityGraph(attributePaths = "category")
    List<EquipmentItem> findByActiveTrueOrderByNameAsc();

    @EntityGraph(attributePaths = "category")
    @Query("select item from EquipmentItem item order by item.name")
    List<EquipmentItem> findAllDetailed();

    @EntityGraph(attributePaths = "category")
    @Query("select item from EquipmentItem item where item.id = :id")
    java.util.Optional<EquipmentItem> findDetailedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from EquipmentItem item where item.id in :ids order by item.id")
    List<EquipmentItem> lockByIdInOrderById(@Param("ids") Collection<UUID> ids);
}
