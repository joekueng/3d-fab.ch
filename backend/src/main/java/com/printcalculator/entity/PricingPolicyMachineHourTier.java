package com.printcalculator.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "pricing_policy_machine_hour_tier")
public class PricingPolicyMachineHourTier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pricing_policy_machine_hour_tier_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pricing_policy_id", nullable = false)
    private PricingPolicy pricingPolicy;

    @Column(name = "tier_start_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal tierStartHours;

    @Column(name = "tier_end_hours", precision = 10, scale = 2)
    private BigDecimal tierEndHours;

    @Column(name = "machine_cost_chf_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal machineCostChfPerHour;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PricingPolicy getPricingPolicy() {
        return pricingPolicy;
    }

    public void setPricingPolicy(PricingPolicy pricingPolicy) {
        this.pricingPolicy = pricingPolicy;
    }

    public BigDecimal getTierStartHours() {
        return tierStartHours;
    }

    public void setTierStartHours(BigDecimal tierStartHours) {
        this.tierStartHours = tierStartHours;
    }

    public BigDecimal getTierEndHours() {
        return tierEndHours;
    }

    public void setTierEndHours(BigDecimal tierEndHours) {
        this.tierEndHours = tierEndHours;
    }

    public BigDecimal getMachineCostChfPerHour() {
        return machineCostChfPerHour;
    }

    public void setMachineCostChfPerHour(BigDecimal machineCostChfPerHour) {
        this.machineCostChfPerHour = machineCostChfPerHour;
    }

}