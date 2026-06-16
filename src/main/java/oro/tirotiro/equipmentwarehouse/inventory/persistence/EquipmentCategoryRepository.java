package oro.tirotiro.equipmentwarehouse.inventory.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentCategoryRepository extends JpaRepository<EquipmentCategory, UUID> {

    Optional<EquipmentCategory> findByNameIgnoreCase(String name);
}
