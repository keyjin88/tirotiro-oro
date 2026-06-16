package oro.tirotiro.equipmentwarehouse.inventory.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import oro.tirotiro.equipmentwarehouse.auth.persistence.User;

public interface EquipmentItemRepository extends JpaRepository<EquipmentItem, UUID>, JpaSpecificationExecutor<EquipmentItem> {

    @Override
    @EntityGraph(attributePaths = {"category", "owner"})
    java.util.List<EquipmentItem> findAll(Specification<EquipmentItem> spec, Sort sort);

    @EntityGraph(attributePaths = {"category", "owner"})
    List<EquipmentItem> findByActiveTrueOrderByNameAsc();

    long countByCategory_Id(UUID categoryId);

    @EntityGraph(attributePaths = {"category", "owner"})
    @Query("select item from EquipmentItem item order by item.name")
    List<EquipmentItem> findAllDetailed();

    @EntityGraph(attributePaths = {"category", "owner"})
    @Query("select item from EquipmentItem item where item.id = :id")
    java.util.Optional<EquipmentItem> findDetailedById(@Param("id") UUID id);

    @Query("""
            select item from EquipmentItem item
            join item.category category
            where item.active = true
              and (
                lower(item.name) like lower(concat('%', :query, '%'))
                or lower(item.manufacturer) like lower(concat('%', :query, '%'))
                or lower(item.model) like lower(concat('%', :query, '%'))
                or lower(category.name) like lower(concat('%', :query, '%'))
                or exists (
                    select unit.id from EquipmentUnit unit
                    where unit.equipmentItem = item
                      and unit.archived = false
                      and (
                        lower(unit.inventoryNumber) like lower(concat('%', :query, '%'))
                        or lower(unit.serialNumber) like lower(concat('%', :query, '%'))
                      )
                )
              )
            order by item.name
            """)
    @EntityGraph(attributePaths = {"category", "owner"})
    List<EquipmentItem> searchActiveForBooking(@Param("query") String query, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from EquipmentItem item where item.id in :ids order by item.id")
    List<EquipmentItem> lockByIdInOrderById(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EquipmentItem item set item.deletedBy = null where item.deletedBy.id = :userId")
    void clearDeletedByReferences(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EquipmentItem item set item.owner = :newOwner where item.owner.id = :userId")
    void reassignOwnerReferences(@Param("userId") UUID userId, @Param("newOwner") User newOwner);
}
