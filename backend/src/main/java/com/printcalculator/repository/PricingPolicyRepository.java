package com.printcalculator.repository;

import com.printcalculator.entity.PricingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingPolicyRepository extends JpaRepository<PricingPolicy, Long> {
    PricingPolicy findFirstByIsActiveTrueOrderByValidFromDesc();
}