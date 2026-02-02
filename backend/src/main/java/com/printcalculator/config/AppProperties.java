package com.printcalculator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "pricing")
public class AppProperties {

    private double filamentCostPerKg;
    private double machineCostPerHour;
    private double energyCostPerKwh;
    private double printerPowerWatts;
    private double markupPercent;

    private String slicerPath;
    private String profilesRoot;

    // Getters and Setters needed for Spring binding
    
    public double getFilamentCostPerKg() { return filamentCostPerKg; }
    public void setFilamentCostPerKg(double filamentCostPerKg) { this.filamentCostPerKg = filamentCostPerKg; }

    public double getMachineCostPerHour() { return machineCostPerHour; }
    public void setMachineCostPerHour(double machineCostPerHour) { this.machineCostPerHour = machineCostPerHour; }

    public double getEnergyCostPerKwh() { return energyCostPerKwh; }
    public void setEnergyCostPerKwh(double energyCostPerKwh) { this.energyCostPerKwh = energyCostPerKwh; }

    public double getPrinterPowerWatts() { return printerPowerWatts; }
    public void setPrinterPowerWatts(double printerPowerWatts) { this.printerPowerWatts = printerPowerWatts; }

    public double getMarkupPercent() { return markupPercent; }
    public void setMarkupPercent(double markupPercent) { this.markupPercent = markupPercent; }

    // Slicer props are not under "pricing" prefix in properties file?
    // Wait, in application.properties I put them at root level/custom. 
    // Let's fix this class to map correctly or change prefix.
    // I'll make a separate section or just bind manually. 
    // Actually, I'll just add @Value in services for simplicity or fix the prefix structure.
    // Let's stick to standard @Value for simple paths if this is messy. 
    // Or better, creating a dedicated SlicerProperties.
}
