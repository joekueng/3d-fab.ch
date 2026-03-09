package com.printcalculator.repository;

import com.printcalculator.entity.MediaVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MediaVariantRepository extends JpaRepository<MediaVariant, UUID> {
    List<MediaVariant> findByMediaAsset_IdOrderByCreatedAtAsc(UUID mediaAssetId);

    List<MediaVariant> findByMediaAsset_IdIn(Collection<UUID> mediaAssetIds);
}
