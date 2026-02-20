package org.openhab.binding.idsmyrv.internal.idscan;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * IDS-CAN device types.
 *
 * For the MVP, we only define DIMMABLE_LIGHT.
 * Other device types can be added later.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public enum DeviceType {
    LATCHING_RELAY(0x03, "Latching Relay"),
    MOMENTARY_H_BRIDGE(0x06, "Momentary H-Bridge"),
    TANK_SENSOR(0x0A, "Tank Sensor"),
    RGB_LIGHT(0x0D, "RGB Light"),
    HVAC_CONTROL(0x10, "HVAC Control"),
    DIMMABLE_LIGHT(0x14, "Dimmable Light"),
    LATCHING_RELAY_TYPE_2(0x1E, "Latching Relay Type 2"),
    MOMENTARY_H_BRIDGE_T2(0x21, "Momentary H-Bridge Type 2"),
    UNKNOWN(0xFF, "Unknown");

    private final int value;
    private final String name;

    DeviceType(int value, String name) {
        this.value = value;
        this.name = name;
    }

    /**
     * Get the numeric value of this device type.
     *
     * @return The device type value
     */
    public int getValue() {
        return value;
    }

    /**
     * Get the human-readable name of this device type.
     *
     * @return The device type name
     */
    public String getName() {
        return name;
    }

    /**
     * Get a DeviceType by its numeric value.
     *
     * @param value The device type value
     * @return The DeviceType, or UNKNOWN if not recognized
     */
    public static DeviceType fromValue(int value) {
        for (DeviceType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return UNKNOWN;
    }

    @Override
    public String toString() {
        return String.format("%s(0x%02X)", name, value);
    }
}
