import re
import os
import logging
from typing import Dict, Any, Optional
from config import settings

logger = logging.getLogger(__name__)

class GCodeParser:
    @staticmethod
    def parse_metadata(gcode_path: str) -> Dict[str, Any]:
        """
        Parses the G-code to extract estimated time and material usage.
        Scans both the beginning (header) and end (footer) of the file.
        """
        stats = {
            "print_time_seconds": 0,
            "filament_length_mm": 0,
            "filament_volume_mm3": 0,
            "filament_weight_g": 0,
            "slicer_estimated_cost": 0
        }

        if not os.path.exists(gcode_path):
            logger.warning(f"GCode file not found for parsing: {gcode_path}")
            return stats

        try:
            with open(gcode_path, 'r', encoding='utf-8', errors='ignore') as f:
                # Read header (first 500 lines)
                header_lines = [f.readline().strip() for _ in range(500) if f]

                # Read footer (last 20KB)
                f.seek(0, 2)
                file_size = f.tell()
                read_len = min(file_size, 20480)
                f.seek(file_size - read_len)
                footer_lines = f.read().splitlines()

                all_lines = header_lines + footer_lines

                for line in all_lines:
                    line = line.strip()
                    if not line.startswith(";"): 
                        continue
                    
                    GCodeParser._parse_line(line, stats)

                # Fallback calculation
                if stats["filament_weight_g"] == 0 and stats["filament_length_mm"] > 0:
                    GCodeParser._calculate_weight_fallback(stats)
                    
        except Exception as e:
            logger.error(f"Error parsing G-code: {e}")

        return stats

    @staticmethod
    def _parse_line(line: str, stats: Dict[str, Any]):
        # Parse Time
        if "estimated printing time =" in line: # Header
            time_str = line.split("=")[1].strip()
            logger.info(f"Parsing time string (Header): '{time_str}'")
            stats["print_time_seconds"] = GCodeParser._parse_time_string(time_str)
        elif "total estimated time:" in line: # Footer
            parts = line.split("total estimated time:")
            if len(parts) > 1:
                time_str = parts[1].strip()
                logger.info(f"Parsing time string (Footer): '{time_str}'")
                stats["print_time_seconds"] = GCodeParser._parse_time_string(time_str)

        # Parse Filament info
        if "filament used [g] =" in line:
            try:
                stats["filament_weight_g"] = float(line.split("=")[1].strip())
            except ValueError: pass
        
        if "filament used [mm] =" in line:
            try:
                stats["filament_length_mm"] = float(line.split("=")[1].strip())
            except ValueError: pass

        if "filament used [cm3] =" in line:
            try:
                 # cm3 to mm3
                stats["filament_volume_mm3"] = float(line.split("=")[1].strip()) * 1000
            except ValueError: pass

    @staticmethod
    def _calculate_weight_fallback(stats: Dict[str, Any]):
        # Assumes 1.75mm diameter and PLA density 1.24
        radius = 1.75 / 2
        volume_mm3 = 3.14159 * (radius ** 2) * stats["filament_length_mm"]
        volume_cm3 = volume_mm3 / 1000.0
        stats["filament_weight_g"] = volume_cm3 * 1.24

    @staticmethod
    def _parse_time_string(time_str: str) -> int:
        """
        Converts '1d 2h 3m 4s' or 'HH:MM:SS' to seconds.
        """
        total_seconds = 0
        
        # Try HH:MM:SS or MM:SS format
        if ':' in time_str:
            parts = time_str.split(':')
            parts = [int(p) for p in parts]
            if len(parts) == 3: # HH:MM:SS
                total_seconds = parts[0] * 3600 + parts[1] * 60 + parts[2]
            elif len(parts) == 2: # MM:SS
                total_seconds = parts[0] * 60 + parts[1]
            return total_seconds

        # Original regex parsing for "1h 2m 3s"
        days = re.search(r'(\d+)d', time_str)
        hours = re.search(r'(\d+)h', time_str)
        mins = re.search(r'(\d+)m', time_str)
        secs = re.search(r'(\d+)s', time_str)

        if days: total_seconds += int(days.group(1)) * 86400
        if hours: total_seconds += int(hours.group(1)) * 3600
        if mins: total_seconds += int(mins.group(1)) * 60
        if secs: total_seconds += int(secs.group(1))
        
        return total_seconds


class QuoteCalculator:
    @staticmethod
    def calculate(stats: Dict[str, Any]) -> Dict[str, Any]:
        """
        Calculates the final quote based on parsed stats and settings.
        """
        # 1. Material Cost
        # Cost per gram = (Cost per kg / 1000)
        material_cost = (stats["filament_weight_g"] / 1000.0) * settings.FILAMENT_COST_PER_KG

        # 2. Machine Time Cost
        # Cost per second = (Cost per hour / 3600)
        print_time_hours = stats["print_time_seconds"] / 3600.0
        machine_cost = print_time_hours * settings.MACHINE_COST_PER_HOUR

        # 3. Energy Cost
        # kWh = (Watts / 1000) * hours
        kwh_used = (settings.PRINTER_POWER_WATTS / 1000.0) * print_time_hours
        energy_cost = kwh_used * settings.ENERGY_COST_PER_KWH

        # Subtotal
        subtotal = material_cost + machine_cost + energy_cost

        # 4. Markup
        markup_factor = 1.0 + (settings.MARKUP_PERCENT / 100.0)
        total_price = subtotal * markup_factor

        logger.info("Cost Calculation:")
        logger.info(f"  - Use: {stats['filament_weight_g']:.2f}g @ {settings.FILAMENT_COST_PER_KG}€/kg = {material_cost:.2f}€")
        logger.info(f"  - Time: {print_time_hours:.4f}h @ {settings.MACHINE_COST_PER_HOUR}€/h = {machine_cost:.2f}€")
        logger.info(f"  - Power: {kwh_used:.4f}kWh @ {settings.ENERGY_COST_PER_KWH}€/kWh = {energy_cost:.2f}€")
        logger.info(f"  - Subtotal: {subtotal:.2f}€")
        logger.info(f"  - Total (Markup {settings.MARKUP_PERCENT}%): {total_price:.2f}€")

        return {
            "breakdown": {
                "material_cost": round(material_cost, 2),
                "machine_cost": round(machine_cost, 2),
                "energy_cost": round(energy_cost, 2),
                "subtotal": round(subtotal, 2),
                "markup_amount": round(total_price - subtotal, 2)
            },
            "total_price": round(total_price, 2),
            "currency": "EUR"
        }
