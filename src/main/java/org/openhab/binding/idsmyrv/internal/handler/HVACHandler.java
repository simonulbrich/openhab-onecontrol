package org.openhab.binding.idsmyrv.internal.handler;

import static org.openhab.binding.idsmyrv.internal.IDSMyRVBindingConstants.*;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;
import org.openhab.binding.idsmyrv.internal.config.DeviceConfiguration;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.binding.idsmyrv.internal.idscan.CommandBuilder;
import org.openhab.binding.idsmyrv.internal.idscan.IDSMessage;
import org.openhab.binding.idsmyrv.internal.idscan.MessageType;
import org.openhab.binding.idsmyrv.internal.idscan.SessionManager;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;

/**
 * The {@link HVACHandler} handles commands and status updates for HVAC/Climate Zone devices.
 *
 * Based on C# LogicalDeviceClimateZone:
 * - Status: 8 bytes (command, lowTrip, highTrip, zoneStatus, indoorTemp, outdoorTemp)
 * - Command: 3 bytes (command byte, lowTrip, highTrip)
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class HVACHandler extends BaseIDSMyRVDeviceHandler {

    private @Nullable DeviceConfiguration config;
    private @Nullable ScheduledFuture<?> commandTimeoutTask;

    // Current state
    private int heatMode = 0; // 0=Off, 1=Heating, 2=Cooling, 3=Both, 4=RunSchedule
    private int heatSource = 0; // 0=PreferGas, 1=PreferHeatPump, 2=Other, 3=Reserved
    private int fanMode = 0; // 0=Auto, 1=High, 2=Low, 3=Reserved
    private int lowTripTemp = 70;
    private int highTripTemp = 75;
    private float indoorTemp = 0.0f;
    private float outdoorTemp = 0.0f;
    private int zoneStatus = 0; // ClimateZoneStatus enum value
    private boolean waitingForStatus = false;

    public HVACHandler(Thing thing) {
        super(thing);
    }

    @Override
    protected void initializeDevice() {
        logger.debug("Initializing HVAC Handler for thing {}", getThing().getUID());

        config = getConfigAs(DeviceConfiguration.class);

        if (!config.isValid()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Invalid device address: must be between 1 and 255");
            return;
        }

        // Check bridge status
        Bridge bridge = getBridge();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED, "No bridge configured");
            return;
        }

        ThingStatusInfo bridgeStatus = bridge.getStatusInfo();
        if (bridgeStatus.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Bridge is not online");
            return;
        }

        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public Address getDeviceAddress() {
        DeviceConfiguration cfg = config;
        return new Address(cfg != null ? cfg.address : 0);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            // Refresh state - we'll update from STATUS messages
            return;
        }

        DeviceConfiguration cfg = config;
        if (cfg == null) {
            logger.warn("Cannot handle command: configuration not available");
            return;
        }

        String channelId = channelUID.getId();

        try {
            if (channelId.equals(CHANNEL_HVAC_MODE)) {
                handleModeCommand(command);
            } else if (channelId.equals(CHANNEL_HVAC_HEAT_SOURCE)) {
                handleHeatSourceCommand(command);
            } else if (channelId.equals(CHANNEL_HVAC_FAN_MODE)) {
                handleFanModeCommand(command);
            } else if (channelId.equals(CHANNEL_HVAC_LOW_TEMP)) {
                handleLowTempCommand(command);
            } else if (channelId.equals(CHANNEL_HVAC_HIGH_TEMP)) {
                handleHighTempCommand(command);
            } else {
                logger.debug("Unknown channel: {}", channelId);
            }
        } catch (Exception e) {
            logger.error("Error handling command for channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    private void handleModeCommand(Command command) throws Exception {
        if (!(command instanceof StringType)) {
            logger.warn("Invalid command type for mode: {}", command.getClass().getSimpleName());
            return;
        }

        String modeStr = command.toString();
        int newMode;

        // Map string to enum value (from C# ClimateZoneHeatMode)
        switch (modeStr.toUpperCase()) {
            case "OFF":
                newMode = 0;
                break;
            case "HEAT":
            case "HEATING":
                newMode = 1;
                break;
            case "COOL":
            case "COOLING":
                newMode = 2;
                break;
            case "BOTH":
            case "HEATCOOL":
                newMode = 3;
                break;
            case "RUNSCHEDULE":
                newMode = 4;
                break;
            default:
                logger.warn("Unknown HVAC mode: {}", modeStr);
                return;
        }

        sendHVACCommand(newMode, heatSource, fanMode, lowTripTemp, highTripTemp);
    }

    private void handleHeatSourceCommand(Command command) throws Exception {
        if (!(command instanceof StringType)) {
            logger.warn("Invalid command type for heat source: {}", command.getClass().getSimpleName());
            return;
        }

        String sourceStr = command.toString();
        int newSource;

        // Map string to enum value (from C# ClimateZoneHeatSource)
        switch (sourceStr.toUpperCase()) {
            case "GAS":
            case "PREFERGAS":
                newSource = 0;
                break;
            case "HEATPUMP":
            case "PREFERHEATPUMP":
                newSource = 1;
                break;
            case "OTHER":
                newSource = 2;
                break;
            default:
                logger.warn("Unknown heat source: {}", sourceStr);
                return;
        }

        sendHVACCommand(heatMode, newSource, fanMode, lowTripTemp, highTripTemp);
    }

    private void handleFanModeCommand(Command command) throws Exception {
        if (!(command instanceof StringType)) {
            logger.warn("Invalid command type for fan mode: {}", command.getClass().getSimpleName());
            return;
        }

        String fanStr = command.toString();
        int newFan;

        // Map string to enum value (from C# ClimateZoneFanMode)
        switch (fanStr.toUpperCase()) {
            case "AUTO":
                newFan = 0;
                break;
            case "HIGH":
                newFan = 1;
                break;
            case "LOW":
                newFan = 2;
                break;
            default:
                logger.warn("Unknown fan mode: {}", fanStr);
                return;
        }

        sendHVACCommand(heatMode, heatSource, newFan, lowTripTemp, highTripTemp);
    }

    private void handleLowTempCommand(Command command) throws Exception {
        int newTemp;
        if (command instanceof DecimalType) {
            newTemp = ((DecimalType) command).intValue();
        } else {
            logger.warn("Invalid command type for low temperature: {}", command.getClass().getSimpleName());
            return;
        }

        // Clamp to valid range
        newTemp = Math.max(0, Math.min(255, newTemp));

        // Validate against high temp (from C# OpenHABHVAC logic)
        if (heatMode == 1) { // Heating
            if (highTripTemp < newTemp + 2) {
                highTripTemp = newTemp + 2;
            }
        } else if (heatMode == 2) { // Cooling
            if (newTemp > highTripTemp - 2) {
                newTemp = highTripTemp - 2;
            }
        } else if (heatMode == 3) { // Both
            if (highTripTemp < newTemp) {
                if (newTemp - 1 < 255) {
                    highTripTemp = newTemp + 2;
                } else {
                    newTemp = highTripTemp - 2;
                }
            }
        }

        sendHVACCommand(heatMode, heatSource, fanMode, newTemp, highTripTemp);
    }

    private void handleHighTempCommand(Command command) throws Exception {
        int newTemp;
        if (command instanceof DecimalType) {
            newTemp = ((DecimalType) command).intValue();
        } else {
            logger.warn("Invalid command type for high temperature: {}", command.getClass().getSimpleName());
            return;
        }

        // Clamp to valid range
        newTemp = Math.max(0, Math.min(255, newTemp));

        // Validate against low temp (from C# OpenHABHVAC logic)
        if (heatMode == 1) { // Heating
            if (newTemp < lowTripTemp + 2) {
                newTemp = lowTripTemp + 2;
            }
        } else if (heatMode == 2) { // Cooling
            if (lowTripTemp > newTemp - 2) {
                lowTripTemp = newTemp - 2;
            }
        } else if (heatMode == 3) { // Both
            if (lowTripTemp > newTemp) {
                if (lowTripTemp - 1 >= 0) {
                    newTemp = lowTripTemp + 2;
                } else {
                    lowTripTemp = newTemp - 2;
                }
            }
        }

        sendHVACCommand(heatMode, heatSource, fanMode, lowTripTemp, newTemp);
    }

    private void sendHVACCommand(int mode, int source, int fan, int lowTemp, int highTemp) throws Exception {
        DeviceConfiguration cfg = config;
        if (cfg == null) {
            throw new Exception("Configuration not available");
        }

        Address targetAddress = new Address(cfg.address);
        IDSMyRVBridgeHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler == null) {
            throw new Exception("Bridge handler not available");
        }

        // Ensure session is open
        ensureSession(targetAddress, bridgeHandler);

        // Build command
        CommandBuilder builder = new CommandBuilder(bridgeHandler.getSourceAddress(), targetAddress);
        CANMessage message = builder.setHVACCommand(mode, source, fan, lowTemp, highTemp);

        // Send command
        bridgeHandler.sendMessage(message);
        waitingForStatus = true;
        scheduleCommandTimeout();

        logger.debug("Sent HVAC command: mode={}, source={}, fan={}, low={}, high={}", mode, source, fan, lowTemp,
                highTemp);
    }

    // ensureSession() is now inherited from BaseIDSMyRVDeviceHandler

    /**
     * Schedule a timeout for command acknowledgment.
     */
    private void scheduleCommandTimeout() {
        // Cancel any existing timeout
        ScheduledFuture<?> task = commandTimeoutTask;
        if (task != null) {
            task.cancel(false);
        }

        // Set timeout to wait for status update
        commandTimeoutTask = scheduler.schedule(() -> {
            waitingForStatus = false;
            logger.debug("Timeout waiting for HVAC status update");
        }, 2, TimeUnit.SECONDS);
    }

    // getBridgeHandler() is now inherited from BaseIDSMyRVDeviceHandler

    @Override
    public void handleIDSMessage(IDSMessage message) {
        DeviceConfiguration cfg = config;
        if (cfg == null) {
            return;
        }
        int sourceAddr = message.getSourceAddress().getValue();

        // Only process messages from our device
        if (sourceAddr != cfg.address) {
            return;
        }

        // Handle session responses (RESPONSE messages from our target device to us)
        SessionManager sm = sessionManager;
        if (sm != null && message.getMessageType() == MessageType.RESPONSE) {
            IDSMyRVBridgeHandler bridgeHandler = getBridgeHandler();
            if (bridgeHandler != null && message.getTargetAddress().equals(bridgeHandler.getSourceAddress())) {
                logger.debug("🔄 Forwarding HVAC RESPONSE to SessionManager: from device {}, msgData=0x{}", sourceAddr,
                        String.format("%02X", message.getMessageData()));
                sm.processResponse(message);
            }
        }

        // Status updates from our device
        if (message.getMessageType() == MessageType.DEVICE_STATUS) {
            byte[] payload = message.getData();
            logger.debug("🌡️ HVAC DEVICE_STATUS from {}: {}", sourceAddr, formatHex(payload));
            updateHVACStatus(payload);
        }
    }

    /**
     * Update HVAC status from device status message.
     * Based on C# LogicalDeviceClimateZoneStatus:
     * - Byte 0: Command (ClimateZoneCommand - heat mode, heat source, fan mode)
     * - Byte 1: LowTripTemperatureFahrenheit
     * - Byte 2: HighTripTemperatureFahrenheit
     * - Byte 3: ZoneStatus (ClimateZoneStatus, masked with 0x8F)
     * - Bytes 4-5: IndoorTemperatureFahrenheit (signed fixed point 8.8)
     * - Bytes 6-7: OutdoorTemperatureFahrenheit (signed fixed point 8.8)
     */
    private void updateHVACStatus(byte[] data) {
        if (data.length < 8) {
            logger.debug("HVAC status message too short: {} bytes", data.length);
            return;
        }

        // Parse command byte (byte 0)
        byte commandByte = data[0];
        int newHeatMode = commandByte & 0x07; // Bits 0-2
        int newHeatSource = (commandByte & 0x30) >> 4; // Bits 4-5
        int newFanMode = (commandByte & 0xC0) >> 6; // Bits 6-7

        // Parse temperatures
        int newLowTrip = data[1] & 0xFF;
        int newHighTrip = data[2] & 0xFF;
        int newZoneStatus = data[3] & 0x8F; // Mask with 0x8F to get zone status

        // Parse indoor temperature (signed fixed point 8.8, bytes 4-5, big-endian)
        int indoorRaw = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
        float newIndoorTemp = parseSignedFixedPoint88(indoorRaw);

        // Parse outdoor temperature (signed fixed point 8.8, bytes 6-7, big-endian)
        int outdoorRaw = ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
        float newOutdoorTemp = parseSignedFixedPoint88(outdoorRaw);

        // Update state if changed
        boolean changed = false;
        if (heatMode != newHeatMode) {
            heatMode = newHeatMode;
            updateState(CHANNEL_HVAC_MODE, new StringType(getModeString(newHeatMode)));
            changed = true;
        }
        if (heatSource != newHeatSource) {
            heatSource = newHeatSource;
            updateState(CHANNEL_HVAC_HEAT_SOURCE, new StringType(getHeatSourceString(newHeatSource)));
            changed = true;
        }
        if (fanMode != newFanMode) {
            fanMode = newFanMode;
            updateState(CHANNEL_HVAC_FAN_MODE, new StringType(getFanModeString(newFanMode)));
            changed = true;
        }
        if (lowTripTemp != newLowTrip) {
            lowTripTemp = newLowTrip;
            updateState(CHANNEL_HVAC_LOW_TEMP, new DecimalType(newLowTrip));
            changed = true;
        }
        if (highTripTemp != newHighTrip) {
            highTripTemp = newHighTrip;
            updateState(CHANNEL_HVAC_HIGH_TEMP, new DecimalType(newHighTrip));
            changed = true;
        }
        if (Math.abs(indoorTemp - newIndoorTemp) > 0.1f) {
            indoorTemp = newIndoorTemp;
            updateState(CHANNEL_HVAC_INDOOR_TEMP, new DecimalType(newIndoorTemp));
            changed = true;
        }
        if (Math.abs(outdoorTemp - newOutdoorTemp) > 0.1f) {
            outdoorTemp = newOutdoorTemp;
            updateState(CHANNEL_HVAC_OUTDOOR_TEMP, new DecimalType(newOutdoorTemp));
            changed = true;
        }
        if (zoneStatus != newZoneStatus) {
            zoneStatus = newZoneStatus;
            updateState(CHANNEL_HVAC_STATUS, new StringType(getZoneStatusString(newZoneStatus)));
            changed = true;
        }

        if (changed) {
            logger.debug(
                    "Updated HVAC status: mode={}, source={}, fan={}, low={}, high={}, indoor={}, outdoor={}, status={}",
                    newHeatMode, newHeatSource, newFanMode, newLowTrip, newHighTrip, newIndoorTemp, newOutdoorTemp,
                    newZoneStatus);
        }

        waitingForStatus = false;
        ScheduledFuture<?> task = commandTimeoutTask;
        if (task != null) {
            task.cancel(false);
            commandTimeoutTask = null;
        }
    }

    /**
     * Format byte array as hex string for debug logging.
     */
    private String formatHex(byte[] data) {
        if (data.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(String.format("%02X", data[i] & 0xFF));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Parse signed fixed point 8.8 format.
     * Based on C# FixedPointSignedBigEndian8X8.ToFloat()
     */
    private float parseSignedFixedPoint88(int raw) {
        // Convert to signed 16-bit
        short signed = (short) raw;
        // Fixed point 8.8: divide by 256.0
        return signed / 256.0f;
    }

    private String getModeString(int mode) {
        switch (mode) {
            case 0:
                return "OFF";
            case 1:
                return "HEAT";
            case 2:
                return "COOL";
            case 3:
                return "BOTH";
            case 4:
                return "RUNSCHEDULE";
            default:
                return "UNKNOWN";
        }
    }

    private String getHeatSourceString(int source) {
        switch (source) {
            case 0:
                return "GAS";
            case 1:
                return "HEATPUMP";
            case 2:
                return "OTHER";
            default:
                return "UNKNOWN";
        }
    }

    private String getFanModeString(int fan) {
        switch (fan) {
            case 0:
                return "AUTO";
            case 1:
                return "HIGH";
            case 2:
                return "LOW";
            default:
                return "UNKNOWN";
        }
    }

    private String getZoneStatusString(int status) {
        // Based on C# ClimateZoneStatus enum
        switch (status) {
            case 0:
                return "OFF";
            case 1:
                return "IDLE";
            case 2:
                return "COOLING";
            case 3:
                return "HEAT_PUMP";
            case 4:
                return "ELEC_FURNACE";
            case 5:
                return "GAS_FURNACE";
            case 6:
                return "GAS_OVERRIDE";
            case 7:
                return "DEAD_TIME";
            case 8:
                return "LOAD_SHEDDING";
            case 128:
                return "FAIL_OFF";
            case 129:
                return "FAIL_IDLE";
            case 130:
                return "FAIL_COOLING";
            case 131:
                return "FAIL_HEAT_PUMP";
            case 132:
                return "FAIL_ELEC_FURNACE";
            case 133:
                return "FAIL_GAS_FURNACE";
            case 134:
                return "FAIL_GAS_OVERRIDE";
            case 135:
                return "FAIL_DEAD_TIME";
            case 136:
                return "FAIL_SHEDDING";
            default:
                return "UNKNOWN";
        }
    }

    @Override
    public void dispose() {
        // Cancel any pending tasks
        ScheduledFuture<?> task = commandTimeoutTask;
        if (task != null) {
            task.cancel(true);
            commandTimeoutTask = null;
        }

        super.dispose();
    }
}
