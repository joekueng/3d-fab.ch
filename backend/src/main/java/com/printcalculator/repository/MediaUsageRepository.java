package com.printcalculator.repository;

import com.printcalculator.entity.MediaUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MediaUsageRepository extends JpaRepository<MediaUsage, UUID> {
    List<MediaUsage> findByMediaAsset_IdOrderBySortOrderAscCreatedAtAsc(UUID mediaAssetId);

    List<MediaUsage> findByMediaAsset_IdIn(Collection<UUID> mediaAssetIds);

    List<MediaUsage> findByUsageTypeAndUsageKeyAndIsActiveTrueOrderBySortOrderAscCreatedAtAsc(String usageType,
                                                                                               String usageKey);

    @Query("""
            select usage from MediaUsage usage
            where usage.usageType = :usageType
              and usage.usageKey = :usageKey
              and ((:ownerId is null and usage.ownerId is null) or usage.ownerId = :ownerId)
            order by usage.sortOrder asc, usage.createdAt asc
            """)
    List<MediaUsage> findByUsageScope(@Param("usageType") String usageType,
                                      @Param("usageKey") String usageKey,
                                      @Param("ownerId") UUID ownerId);
}
