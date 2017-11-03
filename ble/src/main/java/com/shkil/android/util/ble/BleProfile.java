package com.shkil.android.util.ble;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.util.UUID.fromString;

/**
 * Contains UUIDs of BLE services, characteristics, etc
 */
public final class BleProfile {

    public interface Descriptors {
        UUID CLIENT_CHARACTERISTIC_CONFIG = fromString("00002902-0000-1000-8000-00805f9b34fb");
    }

    public interface BatteryService {
        java.util.UUID UUID = fromString("0000180f-0000-1000-8000-00805f9b34fb");

        interface Characteristics {
            java.util.UUID BATTERY_LEVEL = fromString("00002a19-0000-1000-8000-00805f9b34fb");
        }
    }

    public interface DeviceInformationService {
        java.util.UUID UUID = fromString("0000180a-0000-1000-8000-00805f9b34fb");

        interface Characteristics {
            java.util.UUID FIRMWARE_REVISION = fromString("00002a26-0000-1000-8000-00805f9b34fb");
            java.util.UUID SOFTWARE_REVISION = fromString("00002a28-0000-1000-8000-00805f9b34fb");
        }
    }

    public static String getSpecificationName(UUID uuid) {
        return NAMES.get(uuid);
    }

    public static final Map<UUID, String> NAMES = new HashMap<>();

    static {
        NAMES.put(BatteryService.UUID, "Battery Service");
        NAMES.put(BatteryService.Characteristics.BATTERY_LEVEL, "Battery Level");
        NAMES.put(DeviceInformationService.UUID, "Device Information Service");
        NAMES.put(DeviceInformationService.Characteristics.FIRMWARE_REVISION, "Firmware Revision");
        NAMES.put(DeviceInformationService.Characteristics.SOFTWARE_REVISION, "Software Revision");
        NAMES.put(Descriptors.CLIENT_CHARACTERISTIC_CONFIG, "Client Characteristic Config");
    }

}
