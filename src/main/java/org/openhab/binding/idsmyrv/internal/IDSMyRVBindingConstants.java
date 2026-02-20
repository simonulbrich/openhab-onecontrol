package org.openhab.binding.idsmyrv.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link IDSMyRVBindingConstants} class defines common constants used across
 * the IDS MyRV binding.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class IDSMyRVBindingConstants {

    // Binding ID
    public static final String BINDING_ID = "idsmyrv";

    // Bridge Thing Type
    public static final ThingTypeUID THING_TYPE_BRIDGE = new ThingTypeUID(BINDING_ID, "gateway");

    // Thing Types
    public static final ThingTypeUID THING_TYPE_LIGHT = new ThingTypeUID(BINDING_ID, "light");
    public static final ThingTypeUID THING_TYPE_RGB_LIGHT = new ThingTypeUID(BINDING_ID, "rgblight");
    public static final ThingTypeUID THING_TYPE_TANK_SENSOR = new ThingTypeUID(BINDING_ID, "tanksensor");
    public static final ThingTypeUID THING_TYPE_LATCHING_RELAY = new ThingTypeUID(BINDING_ID, "latchingrelay");
    public static final ThingTypeUID THING_TYPE_MOMENTARY_H_BRIDGE = new ThingTypeUID(BINDING_ID, "momentaryhbridge");
    public static final ThingTypeUID THING_TYPE_HVAC = new ThingTypeUID(BINDING_ID, "hvac");

    // Bridge Configuration Properties
    public static final String CONFIG_IP_ADDRESS = "ipAddress";
    public static final String CONFIG_PORT = "port";
    public static final String CONFIG_SOURCE_ADDRESS = "sourceAddress";
    public static final String CONFIG_VERBOSE = "verbose";
    public static final String CONFIG_AUTO_DISCOVERY = "autoDiscovery";

    // Thing Configuration Properties
    public static final String CONFIG_DEVICE_ADDRESS = "address";

    // Common Channel IDs (shared across device types)
    public static final String CHANNEL_LOCKOUT_STATUS = "lockout_status";
    public static final String CHANNEL_LOCKOUT_LEVEL = "lockout_level";

    // Channel IDs for Light
    public static final String CHANNEL_SWITCH = "switch";
    public static final String CHANNEL_BRIGHTNESS = "brightness";
    public static final String CHANNEL_MODE = "mode";
    public static final String CHANNEL_SLEEP = "sleep";
    public static final String CHANNEL_TIME1 = "time1";
    public static final String CHANNEL_TIME2 = "time2";

    // Channel IDs for RGB Light
    public static final String CHANNEL_RGB_COLOR = "color";
    public static final String CHANNEL_RGB_MODE = "mode";
    public static final String CHANNEL_RGB_SPEED = "speed";
    public static final String CHANNEL_RGB_SLEEP = "sleep";

    // Channel IDs for Tank Sensor
    public static final String CHANNEL_TANK_LEVEL = "level";

    // Channel IDs for Latching Relay
    public static final String CHANNEL_RELAY_SWITCH = "switch";
    public static final String CHANNEL_RELAY_FAULT = "fault";
    public static final String CHANNEL_RELAY_OUTPUT_DISABLED = "output_disable";
    public static final String CHANNEL_RELAY_POSITION = "position";
    public static final String CHANNEL_RELAY_CURRENT_DRAW = "current";
    public static final String CHANNEL_RELAY_DTC_REASON = "dtc_reason";

    // Channel IDs for Momentary H-Bridge
    public static final String CHANNEL_H_BRIDGE_DIRECTION = "direction";
    public static final String CHANNEL_H_BRIDGE_FAULT = "fault";
    public static final String CHANNEL_H_BRIDGE_OUTPUT_DISABLED = "output_disable";
    public static final String CHANNEL_H_BRIDGE_POSITION = "position";
    public static final String CHANNEL_H_BRIDGE_CURRENT_DRAW = "current";
    public static final String CHANNEL_H_BRIDGE_DTC_REASON = "dtc_reason";

    // Channel IDs for HVAC
    public static final String CHANNEL_HVAC_MODE = "hvac_mode";
    public static final String CHANNEL_HVAC_HEAT_SOURCE = "heat_source";
    public static final String CHANNEL_HVAC_FAN_MODE = "fan_mode";
    public static final String CHANNEL_HVAC_LOW_TEMP = "low_temperature";
    public static final String CHANNEL_HVAC_HIGH_TEMP = "high_temperature";
    public static final String CHANNEL_HVAC_INDOOR_TEMP = "inside_temperature";
    public static final String CHANNEL_HVAC_OUTDOOR_TEMP = "outside_temperature";
    public static final String CHANNEL_HVAC_STATUS = "status";

    // Device Properties
    public static final String PROPERTY_DEVICE_TYPE = "deviceType"; // Numeric device type value
    public static final String PROPERTY_DEVICE_TYPE_NAME = "deviceTypeName"; // Human-readable device type name
    public static final String PROPERTY_FUNCTION_CLASS = "functionClass"; // Function name/class
    public static final String PROPERTY_DEVICE_NAME = "deviceName"; // Device name (function name)
    public static final String PROPERTY_INSTANCE = "instance"; // Device instance number
    public static final String PROPERTY_DEVICE_CAPABILITIES = "deviceCapabilities"; // Device capabilities byte (Type 2
                                                                                    // relays)
}
