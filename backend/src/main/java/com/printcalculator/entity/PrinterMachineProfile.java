package com.printcalculator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

@Entity
@Table(name = "printer_machine_profile", uniqueConstraints = {
        @UniqueConstraint(name = "ux_printer_machine_profile_machine_nozzle", columnNames = {
                "printer_machine_id", "nozzle_diameter_mm"
        })
})
public class PrinterMachineProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "printer_machine_profile_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "printer_machine_id", nullable = false)
    private PrinterMachine printerMachine;

    @Column(name = "nozzle_diameter_mm", nullable = false, precision = 4, scale = 2)
    private BigDecimal nozzleDiameterMm;

    @Column(name = "orca_machine_profile_name", nullable = false, length = Integer.MAX_VALUE)
    private String orcaMachineProfileName;

    @ColumnDefault("false")
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PrinterMachine getPrinterMachine() {
        return printerMachine;
    }

    public void setPrinterMachine(PrinterMachine printerMachine) {
        this.printerMachine = printerMachine;
    }

    public BigDecimal getNozzleDiameterMm() {
        return nozzleDiameterMm;
    }

    public void setNozzleDiameterMm(BigDecimal nozzleDiameterMm) {
        this.nozzleDiameterMm = nozzleDiameterMm;
    }

    public String getOrcaMachineProfileName() {
        return orcaMachineProfileName;
    }

    public void setOrcaMachineProfileName(String orcaMachineProfileName) {
        this.orcaMachineProfileName = orcaMachineProfileName;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
