package com.printcalculator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "material_orca_profile_map", uniqueConstraints = {
        @UniqueConstraint(name = "ux_material_orca_profile_map_machine_material", columnNames = {
                "printer_machine_profile_id", "filament_material_type_id"
        })
})
public class MaterialOrcaProfileMap {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "material_orca_profile_map_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "printer_machine_profile_id", nullable = false)
    private PrinterMachineProfile printerMachineProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "filament_material_type_id", nullable = false)
    private FilamentMaterialType filamentMaterialType;

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

    public PrinterMachineProfile getPrinterMachineProfile() {
        return printerMachineProfile;
    }

    public void setPrinterMachineProfile(PrinterMachineProfile printerMachineProfile) {
        this.printerMachineProfile = printerMachineProfile;
    }

    public FilamentMaterialType getFilamentMaterialType() {
        return filamentMaterialType;
    }

    public void setFilamentMaterialType(FilamentMaterialType filamentMaterialType) {
        this.filamentMaterialType = filamentMaterialType;
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
