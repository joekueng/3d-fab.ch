package com.printcalculator.repository;

import com.printcalculator.entity.ShopProductModelAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopProductModelAssetRepository extends JpaRepository<ShopProductModelAsset, UUID> {
    Optional<ShopProductModelAsset> findByProduct_Id(UUID productId);

    List<ShopProductModelAsset> findByProduct_IdIn(Collection<UUID> productIds);

    void deleteByProduct_Id(UUID productId);
}
