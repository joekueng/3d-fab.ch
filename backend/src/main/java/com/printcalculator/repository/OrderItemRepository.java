package com.printcalculator.repository;

import com.printcalculator.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrder_Id(UUID orderId);
    boolean existsByFilamentVariant_Id(Long filamentVariantId);
    boolean existsByShopProduct_Id(UUID shopProductId);
    boolean existsByShopProductVariant_Id(UUID shopProductVariantId);
}
