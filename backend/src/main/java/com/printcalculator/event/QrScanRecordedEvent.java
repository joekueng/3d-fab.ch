package com.printcalculator.event;

import java.util.UUID;

public record QrScanRecordedEvent(UUID qrScanEventId, String clientIp) {
}
