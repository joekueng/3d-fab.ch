package com.printcalculator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "filament_variant_orca_override", uniqueConstraints = {
        @UniqueConstraint(name = "ux_filament_variant_orca_override_variant_machine", columnNames = {
                "filament_variant_id", "printer_machine_profile_id"
        })
})
public class FilamentVariantOrcaOverride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "filament_variant_orca_override_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "filament_variant_id", nullable = false)
    private FilamentVariant filamentVariant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "printer_machine_profile_id", nullable = false)
    private PrinterMachineProfile printerMachineProfile;

    @Column(name = "orca_filament_profile_name", nullable = false, length = Integer.MAX_VALUE)
    private String orcaFilamentProfileName;

    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FilamentVariant getFilamentVariant() {
        return filamentVariant;
    }

    public void setFilamentVariant(FilamentVariant filamentVariant) {
        this.filamentVariant = filamentVariant;
    }

    public PrinterMachineProfile getPrinterMachineProfile() {
        return printerMachineProfile;
    }

    public void setPrinterMachineProfile(PrinterMachineProfile printerMachineProfile) {
        this.printerMachineProfile = printerMachineProfile;
    }

    public String getOrcaFilamentProfileName() {
        return orcaFilamentProfileName;
    }

    public void setOrcaFilamentProfileName(String orcaFilamentProfileName) {
        this.orcaFilamentProfileName = orcaFilamentProfileName;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
