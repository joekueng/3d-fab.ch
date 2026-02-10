package com.printcalculator.repository;

import com.printcalculator.entity.PricingPolicyMachineHourTier;
import org.springframework.data.jpa.repository.JpaRepository;

import com.printcalculator.entity.PricingPolicy;
import java.util.List;

public interface PricingPolicyMachineHourTierRepository extends JpaRepository<PricingPolicyMachineHourTier, Long> {
    List<PricingPolicyMachineHourTier> findAllByPricingPolicyOrderByTierStartHoursAsc(PricingPolicy pricingPolicy);
}