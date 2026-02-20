package org.openhab.binding.idsmyrv.internal.idscan;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;

/**
 * Builder for IDS-CAN command messages.
 *
 * This class helps construct command messages for controlling devices
 * on the IDS-CAN bus.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class CommandBuilder {
    // Light command constants
    private static final int LIGHT_CMD_OFF = 0;
    private static final int LIGHT_CMD_ON = 1;
    private static final int LIGHT_CMD_BLINK = 2;
    // Note: LIGHT_CMD_SWELL (3) and LIGHT_CMD_RESTORE (127) are defined but not currently used
    // They may be used in the future for additional light modes

    private final Address sourceAddress;
    private final Address targetAddress;

    /**
     * Create a new command builder.
     *
     * @param sourceAddress The source address (gateway/controller, typically 0-9)
     * @param targetAddress The target device address
     */
    public CommandBuilder(Address sourceAddress, Address targetAddress) {
        this.sourceAddress = sourceAddress;
        this.targetAddress = targetAddress;
    }

    /**
     * Build a command message.
     *
     * @param commandData The command data byte
     * @param payload The command payload
     * @return The CAN message
     */
    private CANMessage buildCommand(int commandData, byte[] payload) {
        IDSMessage ids = IDSMessage.pointToPoint(MessageType.COMMAND, sourceAddress, targetAddress, commandData,
                payload);
        return ids.encode();
    }

    /**
     * Turn a dimmable light ON at the specified brightness.
     *
     * @param brightness The brightness level (0-100)
     * @return The CAN command message
     */
    public CANMessage setLightOn(int brightness) {
        // Normalize brightness to 0-100 range
        if (brightness < 0) {
            brightness = 0;
        }
        if (brightness > 100) {
            brightness = 100;
        }

        // Scale 0-100 to 0-255 for the protocol
        int maxBrightness = (brightness * 255) / 100;

        // Build 8-byte payload for dimmable light command
        // Byte 0: Command (1 = On)
        // Byte 1: MaxBrightness (0-255)
        // Byte 2: Duration (0 = instant)
        // Bytes 3-4: CycleTime1 (0)
        // Bytes 5-6: CycleTime2 (0)
        // Byte 7: Undefined (0)
        byte[] payload = new byte[] { (byte) LIGHT_CMD_ON, (byte) maxBrightness, 0, 0, 0, 0, 0, 0 };

        return buildCommand(0, payload);
    }

    /**
     * Turn a dimmable light OFF but preserve the brightness setting.
     *
     * @param brightness The brightness level to preserve (0-100)
     * @return The CAN command message
     */
    public CANMessage setLightOff(int brightness) {
        // Normalize brightness to 0-100 range
        if (brightness < 0) {
            brightness = 0;
        }
        if (brightness > 100) {
            brightness = 100;
        }

        // Scale 0-100 to 0-255 (preserve the brightness setting even when off)
        int maxBrightness = (brightness * 255) / 100;

        // Build 8-byte payload with command = 0 (Off) but preserve brightness
        byte[] payload = new byte[] { (byte) LIGHT_CMD_OFF, (byte) maxBrightness, 0, 0, 0, 0, 0, 0 };

        return buildCommand(0, payload);
    }

    /**
     * Set a dimmable light to BLINK mode at the specified brightness.
     *
     * @param brightness The brightness level (0-100)
     * @return The CAN command message
     */
    public CANMessage setLightBlink(int brightness) {
        // Normalize brightness to 0-100 range
        if (brightness < 0) {
            brightness = 0;
        }
        if (brightness > 100) {
            brightness = 100;
        }

        // Scale 0-100 to 0-255
        int maxBrightness = (brightness * 255) / 100;

        // Build 8-byte payload with command = 2 (Blink)
        byte[] payload = new byte[] { (byte) LIGHT_CMD_BLINK, (byte) maxBrightness, 0, 0, 0, 0, 0, 0 };

        return buildCommand(0, payload);
    }

    /**
     * Send a full dimmable light command with all parameters.
     * Based on C# LogicalDeviceLightDimmableCommand format:
     * - Byte 0: Mode (0=OFF, 1=ON/DIMMER, 2=BLINK, 3=SWELL)
     * - Byte 1: MaxBrightness (0-255)
     * - Byte 2: Duration/Sleep time (0-255)
     * - Bytes 3-4: CycleTime1 (uint16, big-endian)
     * - Bytes 5-6: CycleTime2 (uint16, big-endian)
     * - Byte 7: Undefined (0)
     *
     * @param mode Mode value (0=OFF, 1=ON, 2=BLINK, 3=SWELL)
     * @param maxBrightness Max brightness (0-100, will be scaled to 0-255)
     * @param duration Sleep time in seconds (0-255)
     * @param cycleTime1 Cycle time 1 in milliseconds (0-65535)
     * @param cycleTime2 Cycle time 2 in milliseconds (0-65535)
     * @return The CAN command message
     */
    public CANMessage setLightCommand(int mode, int maxBrightness, int duration, int cycleTime1, int cycleTime2) {
        // Normalize brightness to 0-100 range, then scale to 0-255
        if (maxBrightness < 0) {
            maxBrightness = 0;
        }
        if (maxBrightness > 100) {
            maxBrightness = 100;
        }
        int maxBrightnessScaled = (maxBrightness * 255) / 100;

        // Normalize duration to 0-255
        if (duration < 0) {
            duration = 0;
        }
        if (duration > 255) {
            duration = 255;
        }

        // Normalize cycle times to 0-65535
        if (cycleTime1 < 0) {
            cycleTime1 = 0;
        }
        if (cycleTime1 > 65535) {
            cycleTime1 = 65535;
        }
        if (cycleTime2 < 0) {
            cycleTime2 = 0;
        }
        if (cycleTime2 > 65535) {
            cycleTime2 = 65535;
        }

        // Build 8-byte payload
        byte[] payload = new byte[8];
        payload[0] = (byte) mode;
        payload[1] = (byte) maxBrightnessScaled;
        payload[2] = (byte) duration;
        payload[3] = (byte) ((cycleTime1 >> 8) & 0xFF); // MSB
        payload[4] = (byte) (cycleTime1 & 0xFF); // LSB
        payload[5] = (byte) ((cycleTime2 >> 8) & 0xFF); // MSB
        payload[6] = (byte) (cycleTime2 & 0xFF); // LSB
        payload[7] = 0; // Undefined

        return buildCommand(0, payload);
    }

    /**
     * Request device identification from the target device.
     *
     * @return The CAN request message
     */
    public CANMessage requestDeviceID() {
        IDSMessage ids = IDSMessage.pointToPoint(MessageType.REQUEST, sourceAddress, targetAddress, 0, new byte[0]);
        return ids.encode();
    }

    /**
     * Request current status from the target device.
     *
     * @return The CAN request message
     */
    public CANMessage requestDeviceStatus() {
        IDSMessage ids = IDSMessage.pointToPoint(MessageType.REQUEST, sourceAddress, targetAddress, 1, new byte[0]);
        return ids.encode();
    }

    /**
     * Turn a latching relay ON (Type 1 format).
     *
     * Based on C# LogicalDeviceRelayBasicCommandType1:
     * - CommandByte = 0
     * - Payload byte 0: Bit 7 (0x80) = Latching bit (always true)
     * Bit 6 (0x40) = ClearingFaultBit (false for normal commands)
     * Bit 1 (0x02) = DisconnectStateBit (set to state)
     * Bit 0 (0x01) = CurrentCommandingStateBit (ON = true)
     * - For ON: 0x80 | 0x02 | 0x01 = 0x83
     *
     * @return The CAN command message
     */
    public CANMessage setRelayOn() {
        return setRelayOnType1();
    }

    /**
     * Turn a latching relay OFF (Type 1 format).
     *
     * Based on C# LogicalDeviceRelayBasicCommandType1:
     * - CommandByte = 0
     * - Payload byte 0: Bit 7 (0x80) = Latching bit (always true)
     * Bit 6 (0x40) = ClearingFaultBit (false for normal commands)
     * Bit 1 (0x02) = DisconnectStateBit (set to state, which is false for OFF)
     * Bit 0 (0x01) = CurrentCommandingStateBit (OFF = false)
     * - For OFF: 0x80 | 0x00 = 0x80
     *
     * @return The CAN command message
     */
    public CANMessage setRelayOff() {
        return setRelayOffType1();
    }

    /**
     * Turn a latching relay ON (Type 1 format).
     *
     * Based on C# LogicalDeviceRelayBasicCommandType1:
     * - CommandByte = 0
     * - Payload byte 0: Bit 7 (0x80) = Latching bit (always true)
     * Bit 6 (0x40) = ClearingFaultBit (false for normal commands)
     * Bit 1 (0x02) = DisconnectStateBit (set to state)
     * Bit 0 (0x01) = CurrentCommandingStateBit (ON = true)
     * - For ON: 0x80 | 0x02 | 0x01 = 0x83
     *
     * @return The CAN command message
     */
    public CANMessage setRelayOnType1() {
        // Type 1 relay format: CommandByte=0, payload byte with command bits
        // ON command: 0x80 (latching) | 0x02 (disconnect=state) | 0x01 (on) = 0x83
        byte[] payload = new byte[] { (byte) 0x83 };
        return buildCommand(0, payload);
    }

    /**
     * Turn a latching relay OFF (Type 1 format).
     *
     * Based on C# LogicalDeviceRelayBasicCommandType1:
     * - CommandByte = 0
     * - Payload byte 0: Bit 7 (0x80) = Latching bit (always true)
     * Bit 6 (0x40) = ClearingFaultBit (false for normal commands)
     * Bit 1 (0x02) = DisconnectStateBit (set to state, which is false for OFF)
     * Bit 0 (0x01) = CurrentCommandingStateBit (OFF = false)
     * - For OFF: 0x80 | 0x00 = 0x80
     *
     * @return The CAN command message
     */
    public CANMessage setRelayOffType1() {
        // Type 1 relay format: CommandByte=0, payload byte with command bits
        // OFF command: 0x80 (latching) | 0x00 (all other bits clear) = 0x80
        byte[] payload = new byte[] { (byte) 0x80 };
        return buildCommand(0, payload);
    }

    /**
     * Turn a latching relay ON (Type 2 format).
     *
     * Based on C# LogicalDeviceRelayBasicLatchingCommandType2:
     * - CommandByte = 1 (ON command)
     * - No payload (empty array)
     *
     * @return The CAN command message
     */
    public CANMessage setRelayOnType2() {
        // Type 2 relay format: CommandByte=1 (ON), no payload
        return buildCommand(1, new byte[0]);
    }

    /**
     * Turn a latching relay OFF (Type 2 format).
     *
     * Based on C# LogicalDeviceRelayBasicLatchingCommandType2:
     * - CommandByte = 0 (OFF command)
     * - No payload (empty array)
     *
     * @return The CAN command message
     */
    public CANMessage setRelayOffType2() {
        // Type 2 relay format: CommandByte=0 (OFF), no payload
        return buildCommand(0, new byte[0]);
    }

    // RGB Light command constants
    public static final int RGB_MODE_OFF = 0;
    public static final int RGB_MODE_ON = 1;
    public static final int RGB_MODE_BLINK = 2;
    public static final int RGB_MODE_JUMP3 = 4;
    public static final int RGB_MODE_JUMP7 = 5;
    public static final int RGB_MODE_FADE3 = 6;
    public static final int RGB_MODE_FADE7 = 7;
    public static final int RGB_MODE_RAINBOW = 8;
    public static final int RGB_MODE_RESTORE = 127;

    /**
     * Set RGB light to ON mode with specified color.
     * Based on C# LogicalDeviceLightRgbCommand.MakeOnCommand:
     * - Byte 0: Mode (1 = On)
     * - Bytes 1-3: RGB color (R, G, B, 0-255 each)
     * - Byte 4: AutoOffDuration (0-255 seconds)
     * - Bytes 5-6: Interval (uint16, big-endian) - preserved from last status
     * - Byte 7: Unused (0)
     *
     * @param red Red component (0-255)
     * @param green Green component (0-255)
     * @param blue Blue component (0-255)
     * @param autoOffDuration Auto-off duration in seconds (0-255)
     * @param interval Interval in milliseconds (0-65535) - typically preserved from last status
     * @return The CAN command message
     */
    public CANMessage setRGBOn(int red, int green, int blue, int autoOffDuration, int interval) {
        // Normalize RGB values to 0-255
        red = Math.max(0, Math.min(255, red));
        green = Math.max(0, Math.min(255, green));
        blue = Math.max(0, Math.min(255, blue));

        // Normalize autoOffDuration to 0-255
        autoOffDuration = Math.max(0, Math.min(255, autoOffDuration));

        // Normalize interval to 0-65535
        interval = Math.max(0, Math.min(65535, interval));

        byte[] payload = new byte[8];
        payload[0] = (byte) RGB_MODE_ON;
        payload[1] = (byte) red;
        payload[2] = (byte) green;
        payload[3] = (byte) blue;
        payload[4] = (byte) autoOffDuration;
        payload[5] = (byte) ((interval >> 8) & 0xFF); // MSB
        payload[6] = (byte) (interval & 0xFF); // LSB
        payload[7] = 0; // Unused

        return buildCommand(0, payload);
    }

    /**
     * Set RGB light to OFF mode.
     * Based on C# LogicalDeviceLightRgbCommand.MakeOffCommand:
     * - Byte 0: Mode (0 = Off)
     * - Bytes 1-3: RGB color (preserved from last status)
     * - Byte 4: AutoOffDuration (preserved from last status)
     * - Bytes 5-6: Interval (preserved from last status)
     * - Byte 7: Unused (0)
     *
     * @param red Red component (0-255) - typically preserved from last status
     * @param green Green component (0-255) - typically preserved from last status
     * @param blue Blue component (0-255) - typically preserved from last status
     * @param autoOffDuration Auto-off duration (0-255) - typically preserved from last status
     * @param interval Interval (0-65535) - typically preserved from last status
     * @return The CAN command message
     */
    public CANMessage setRGBOff(int red, int green, int blue, int autoOffDuration, int interval) {
        // Normalize values
        red = Math.max(0, Math.min(255, red));
        green = Math.max(0, Math.min(255, green));
        blue = Math.max(0, Math.min(255, blue));
        autoOffDuration = Math.max(0, Math.min(255, autoOffDuration));
        interval = Math.max(0, Math.min(65535, interval));

        byte[] payload = new byte[8];
        payload[0] = (byte) RGB_MODE_OFF;
        payload[1] = (byte) red;
        payload[2] = (byte) green;
        payload[3] = (byte) blue;
        payload[4] = (byte) autoOffDuration;
        payload[5] = (byte) ((interval >> 8) & 0xFF);
        payload[6] = (byte) (interval & 0xFF);
        payload[7] = 0;

        return buildCommand(0, payload);
    }

    /**
     * Set RGB light to BLINK mode.
     * Based on C# LogicalDeviceLightRgbCommand.MakeBlinkCommand:
     * - Byte 0: Mode (2 = Blink)
     * - Bytes 1-3: RGB color (R, G, B, 0-255 each)
     * - Byte 4: AutoOffDuration (0-255 seconds)
     * - Byte 5: OnInterval (0-255)
     * - Byte 6: OffInterval (0-255)
     * - Byte 7: Unused (0)
     *
     * @param red Red component (0-255)
     * @param green Green component (0-255)
     * @param blue Blue component (0-255)
     * @param autoOffDuration Auto-off duration in seconds (0-255)
     * @param onInterval On interval (0-255)
     * @param offInterval Off interval (0-255)
     * @return The CAN command message
     */
    public CANMessage setRGBBlink(int red, int green, int blue, int autoOffDuration, int onInterval, int offInterval) {
        // Normalize RGB values
        red = Math.max(0, Math.min(255, red));
        green = Math.max(0, Math.min(255, green));
        blue = Math.max(0, Math.min(255, blue));

        // Normalize other values
        autoOffDuration = Math.max(0, Math.min(255, autoOffDuration));
        onInterval = Math.max(0, Math.min(255, onInterval));
        offInterval = Math.max(0, Math.min(255, offInterval));

        byte[] payload = new byte[8];
        payload[0] = (byte) RGB_MODE_BLINK;
        payload[1] = (byte) red;
        payload[2] = (byte) green;
        payload[3] = (byte) blue;
        payload[4] = (byte) autoOffDuration;
        payload[5] = (byte) onInterval;
        payload[6] = (byte) offInterval;
        payload[7] = 0;

        return buildCommand(0, payload);
    }

    /**
     * Set RGB light to a transition mode (Jump3, Jump7, Fade3, Fade7, Rainbow).
     * Based on C# LogicalDeviceLightRgbCommand.MakeTrasitionModeCommand:
     * - Byte 0: Mode (4=Jump3, 5=Jump7, 6=Fade3, 7=Fade7, 8=Rainbow)
     * - Bytes 1-3: RGB color (0 for transition modes, as they use predefined colors)
     * - Byte 4: AutoOffDuration (0-255 seconds)
     * - Bytes 5-6: Interval (uint16, big-endian, 0-65535 milliseconds)
     * - Byte 7: Unused (0)
     *
     * @param mode Mode value (4=Jump3, 5=Jump7, 6=Fade3, 7=Fade7, 8=Rainbow)
     * @param autoOffDuration Auto-off duration in seconds (0-255)
     * @param interval Interval in milliseconds (0-65535)
     * @return The CAN command message
     */
    public CANMessage setRGBTransition(int mode, int autoOffDuration, int interval) {
        // Validate mode
        if (mode != RGB_MODE_JUMP3 && mode != RGB_MODE_JUMP7 && mode != RGB_MODE_FADE3 && mode != RGB_MODE_FADE7
                && mode != RGB_MODE_RAINBOW) {
            throw new IllegalArgumentException("Invalid transition mode: " + mode);
        }

        // Normalize values
        autoOffDuration = Math.max(0, Math.min(255, autoOffDuration));
        interval = Math.max(0, Math.min(65535, interval));

        byte[] payload = new byte[8];
        payload[0] = (byte) mode;
        payload[1] = 0; // Color not used for transition modes
        payload[2] = 0;
        payload[3] = 0;
        payload[4] = (byte) autoOffDuration;
        payload[5] = (byte) ((interval >> 8) & 0xFF); // MSB
        payload[6] = (byte) (interval & 0xFF); // LSB
        payload[7] = 0;

        return buildCommand(0, payload);
    }

    /**
     * Send RESTORE command to RGB light (restores last ON state).
     * Based on C# LogicalDeviceLightRgbCommand.MakeRestoreCommand:
     * - Byte 0: Mode (127 = Restore)
     * - Bytes 1-7: All zeros
     *
     * @return The CAN command message
     */
    public CANMessage setRGBRestore() {
        byte[] payload = new byte[8];
        payload[0] = (byte) RGB_MODE_RESTORE;
        // Bytes 1-7 are all zeros
        return buildCommand(0, payload);
    }

    // HVAC Control constants (from C# ClimateZoneCommand)
    // Command byte format:
    // Bits 0-2 (0x07): HeatMode (0=Off, 1=Heating, 2=Cooling, 3=Both, 4=RunSchedule)
    // Bits 4-5 (0x30): HeatSource (0=PreferGas, 1=PreferHeatPump, 2=Other, 3=Reserved)
    // Bits 6-7 (0xC0): FanMode (0=Auto, 1=High, 2=Low, 3=Reserved)

    /**
     * Set HVAC command.
     * Based on C# LogicalDeviceClimateZoneCommand:
     * - Byte 0: ClimateZoneCommand (heat mode, heat source, fan mode)
     * - Byte 1: LowTripTemperatureFahrenheit (0-255)
     * - Byte 2: HighTripTemperatureFahrenheit (0-255)
     *
     * @param heatMode Heat mode (0=Off, 1=Heating, 2=Cooling, 3=Both, 4=RunSchedule)
     * @param heatSource Heat source (0=PreferGas, 1=PreferHeatPump, 2=Other, 3=Reserved)
     * @param fanMode Fan mode (0=Auto, 1=High, 2=Low, 3=Reserved)
     * @param lowTripTemp Low trip temperature (0-255)
     * @param highTripTemp High trip temperature (0-255)
     * @return The CAN command message
     */
    public CANMessage setHVACCommand(int heatMode, int heatSource, int fanMode, int lowTripTemp, int highTripTemp) {
        // Normalize values
        heatMode = Math.max(0, Math.min(7, heatMode)) & 0x07;
        heatSource = Math.max(0, Math.min(3, heatSource)) & 0x03;
        fanMode = Math.max(0, Math.min(3, fanMode)) & 0x03;
        lowTripTemp = Math.max(0, Math.min(255, lowTripTemp));
        highTripTemp = Math.max(0, Math.min(255, highTripTemp));

        // Build command byte: heatMode (bits 0-2) | heatSource (bits 4-5) | fanMode (bits 6-7)
        byte commandByte = (byte) (heatMode | (heatSource << 4) | (fanMode << 6));

        byte[] payload = new byte[3];
        payload[0] = commandByte;
        payload[1] = (byte) lowTripTemp;
        payload[2] = (byte) highTripTemp;

        return buildCommand(0, payload);
    }

    // H-Bridge Motor Control constants
    // Type 1: Uses bit flags in byte 0
    //   Bit 0 (0x01): Relay1 (Forward)
    //   Bit 2 (0x04): Relay2 (Reverse)
    //   Bit 6 (0x40): ClearFault
    // Type 2: Uses command byte
    //   0 = Stop
    //   1 = Forward
    //   2 = Reverse
    //   3 = ClearOutputDisabledLatch

    /**
     * Set H-Bridge motor to Forward (Type 1).
     * Based on C# LogicalDeviceRelayHBridgeMomentaryCommandType1:
     * - Byte 0, bit 0 (0x01): Relay1 = true (Forward)
     * - Byte 0, bit 2 (0x04): Relay2 = false
     * - Byte 0, bit 6 (0x40): ClearFault = false
     *
     * @return The CAN command message
     */
    public CANMessage setHBridgeForwardType1() {
        byte[] payload = new byte[1];
        payload[0] = 0x01; // Relay1 on (Forward)
        return buildCommand(0, payload);
    }

    /**
     * Set H-Bridge motor to Reverse (Type 1).
     * Based on C# LogicalDeviceRelayHBridgeMomentaryCommandType1:
     * - Byte 0, bit 0 (0x01): Relay1 = false
     * - Byte 0, bit 2 (0x04): Relay2 = true (Reverse)
     * - Byte 0, bit 6 (0x40): ClearFault = false
     *
     * @return The CAN command message
     */
    public CANMessage setHBridgeReverseType1() {
        byte[] payload = new byte[1];
        payload[0] = 0x04; // Relay2 on (Reverse)
        return buildCommand(0, payload);
    }

    /**
     * Set H-Bridge motor to Stop (Type 1).
     * Based on C# LogicalDeviceRelayHBridgeMomentaryCommandType1.MakeTurnOffRelaysCommand:
     * - Byte 0, bit 0 (0x01): Relay1 = false
     * - Byte 0, bit 2 (0x04): Relay2 = false
     * - Byte 0, bit 6 (0x40): ClearFault = false
     *
     * @return The CAN command message
     */
    public CANMessage setHBridgeStopType1() {
        byte[] payload = new byte[1];
        payload[0] = 0x00; // Both relays off (Stop)
        return buildCommand(0, payload);
    }

    /**
     * Clear H-Bridge fault (Type 1).
     * Based on C# LogicalDeviceRelayHBridgeMomentaryCommandType1:
     * - Byte 0, bit 6 (0x40): ClearFault = true
     *
     * @return The CAN command message
     */
    public CANMessage setHBridgeClearFaultType1() {
        byte[] payload = new byte[1];
        payload[0] = 0x40; // ClearFault bit set
        return buildCommand(0, payload);
    }

    /**
     * Set H-Bridge motor command (Type 2).
     * Based on C# LogicalDeviceRelayHBridgeMomentaryCommandType2:
     * - CommandByte: Command (0=Stop, 1=Forward, 2=Reverse, 3=ClearOutputDisabledLatch)
     * - Data: Empty array (no payload)
     *
     * Note: In Type 2, the command value goes in the CommandByte field, not the payload.
     * This matches the pattern used by Type 2 latching relays.
     *
     * @param command Command value (0=Stop, 1=Forward, 2=Reverse, 3=ClearOutputDisabledLatch)
     * @return The CAN command message
     */
    public CANMessage setHBridgeCommandType2(int command) {
        // Normalize command value
        command = Math.max(0, Math.min(3, command));

        // Type 2 uses command byte, not payload (like Type 2 latching relays)
        return buildCommand(command, new byte[0]);
    }

    /**
     * Set H-Bridge motor to Forward (Type 2).
     *
     * @return The CAN command message
     */
    public CANMessage setHBridgeForwardType2() {
        return setHBridgeCommandType2(1);
    }

    /**
     * Set H-Bridge motor to Reverse (Type 2).
     *
     * @return The CAN command message
     */
    public CANMessage setHBridgeReverseType2() {
        return setHBridgeCommandType2(2);
    }

    /**
     * Set H-Bridge motor to Stop (Type 2).
     *
     * @return The CAN command message
     */
    public CANMessage setHBridgeStopType2() {
        return setHBridgeCommandType2(0);
    }

    /**
     * Clear H-Bridge output disabled latch (Type 2).
     *
     * @return The CAN command message
     */
    public CANMessage setHBridgeClearFaultType2() {
        return setHBridgeCommandType2(3);
    }
}
