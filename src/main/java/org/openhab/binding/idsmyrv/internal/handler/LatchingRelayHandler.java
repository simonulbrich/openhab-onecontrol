package org.openhab.binding.idsmyrv.internal.handler;

import org.openhab.binding.idsmyrv.internal.IDSMyRVBindingConstants;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;
import org.openhab.binding.idsmyrv.internal.config.DeviceConfiguration;
import org.openhab.binding.idsmyrv.internal.idscan.CommandBuilder;
import org.openhab.binding.idsmyrv.internal.idscan.DeviceType;
import org.openhab.binding.idsmyrv.internal.idscan.IDSMessage;
import org.openhab.binding.idsmyrv.internal.idscan.MessageType;
import org.openhab.binding.idsmyrv.internal.idscan.SessionManager;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LatchingRelayHandler} handles latching relay devices.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class LatchingRelayHandler extends BaseIDSMyRVDeviceHandler {

    private @Nullable DeviceConfiguration config;
    private @Nullable ScheduledFuture<?> commandTimeoutTask;

    private boolean isOn = false;
    private boolean waitingForStatus = false;
    private int lastPosition = -1; // Track last position value for change detection

    public LatchingRelayHandler(Thing thing) {
        super(thing);
    }

    @Override
    protected void initializeDevice() {
        logger.debug("Initializing Latching Relay Handler for thing {}", getThing().getUID());

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

    /**
     * Get the device type from thing properties.
     * Defaults to LATCHING_RELAY (Type 1) if not specified.
     */
    private DeviceType getDeviceType() {
        Object deviceTypeValue = getThing().getProperties().get(IDSMyRVBindingConstants.PROPERTY_DEVICE_TYPE);
        if (deviceTypeValue instanceof Integer) {
            return DeviceType.fromValue((Integer) deviceTypeValue);
        } else if (deviceTypeValue instanceof String) {
            try {
                return DeviceType.fromValue(Integer.parseInt((String) deviceTypeValue));
            } catch (NumberFormatException e) {
                logger.debug("Could not parse device type property: {}", deviceTypeValue);
            }
        }
        // Default to Type 1 if not specified
        return DeviceType.LATCHING_RELAY;
    }

    /**
     * Check if position is supported based on device capabilities.
     * Position is supported if SupportsCoarsePosition (bit 1 = 0x02) or
     * SupportsFinePosition (bit 2 = 0x04) flags are set in the capabilities byte.
     *
     * @return true if position is supported, false otherwise
     */
    private boolean isPositionSupported() {
        Object capabilitiesValue = getThing().getProperties().get(IDSMyRVBindingConstants.PROPERTY_DEVICE_CAPABILITIES);
        if (capabilitiesValue == null) {
            return false; // Capabilities not available
        }

        int capabilities;
        if (capabilitiesValue instanceof Integer) {
            capabilities = (Integer) capabilitiesValue;
        } else if (capabilitiesValue instanceof String) {
            try {
                capabilities = Integer.parseInt((String) capabilitiesValue);
            } catch (NumberFormatException e) {
                return false;
            }
        } else {
            return false;
        }

        // Check for SupportsCoarsePosition (0x02) or SupportsFinePosition (0x04)
        boolean supported = (capabilities & 0x02) != 0 || (capabilities & 0x04) != 0;
        logger.debug("Position support check: capabilities=0x{}, supported={}", String.format("%02X", capabilities),
                supported);
        return supported;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            // Refresh state - we'll update from STATUS messages
            return;
        }

        String channelId = channelUID.getId();

        try {
            if (IDSMyRVBindingConstants.CHANNEL_RELAY_SWITCH.equals(channelId)) {
                if (command instanceof OnOffType onOffCommand) {
                    handleSwitchCommand(onOffCommand);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to handle command: {}", e.getMessage());
        }
    }

    /**
     * Handle a switch ON/OFF command.
     */
    private void handleSwitchCommand(OnOffType command) throws Exception {
        DeviceConfiguration cfg = config;
        if (cfg == null) {
            return;
        }

        Address targetAddress = new Address(cfg.address);
        Bridge bridge = getBridge();
        if (bridge == null) {
            throw new Exception("Bridge not available");
        }
        ThingHandler handler = bridge.getHandler();
        if (!(handler instanceof IDSMyRVBridgeHandler)) {
            throw new Exception("Bridge handler not available");
        }
        IDSMyRVBridgeHandler bridgeHandler = (IDSMyRVBridgeHandler) handler;

        // Ensure session is open
        ensureSession(targetAddress, bridgeHandler);

        // Determine device type from thing properties to use correct command format
        DeviceType deviceType = getDeviceType();

        // Build command
        CommandBuilder builder = new CommandBuilder(bridgeHandler.getSourceAddress(), targetAddress);
        CANMessage message;
        if (command == OnOffType.ON) {
            if (deviceType == DeviceType.LATCHING_RELAY_TYPE_2) {
                message = builder.setRelayOnType2();
            } else {
                // Default to Type 1 format (also for LATCHING_RELAY)
                message = builder.setRelayOnType1();
            }
            isOn = true;
            logger.info("🔌 Sending ON command to relay {} (type: {})", cfg.address, deviceType.getName());
        } else {
            if (deviceType == DeviceType.LATCHING_RELAY_TYPE_2) {
                message = builder.setRelayOffType2();
            } else {
                // Default to Type 1 format (also for LATCHING_RELAY)
                message = builder.setRelayOffType1();
            }
            isOn = false;
            logger.info("🔌 Sending OFF command to relay {} (type: {})", cfg.address, deviceType.getName());
        }

        // Send command
        bridgeHandler.sendMessage(message);
        waitingForStatus = true;

        // Set a timeout for status update
        scheduleCommandTimeout();

        logger.debug("Sent {} command to relay {}", command, cfg.address);
    }

    // ensureSession() is now inherited from BaseIDSMyRVDeviceHandler

    /**
     * Schedule a timeout task for command execution.
     */
    private void scheduleCommandTimeout() {
        ScheduledFuture<?> task = commandTimeoutTask;
        if (task != null) {
            task.cancel(false);
        }

        commandTimeoutTask = scheduler.schedule(() -> {
            if (waitingForStatus) {
                logger.warn("Command timeout - no status update received");
                waitingForStatus = false;
            }
        }, 5, TimeUnit.SECONDS);
    }

    @Override
    public void handleIDSMessage(IDSMessage message) {
        DeviceConfiguration cfg = config;
        if (cfg == null) {
            return;
        }

        // Process session responses (RESPONSE messages from our target device to us)
        SessionManager sm = sessionManager;
        if (sm != null && message.getMessageType() == MessageType.RESPONSE) {
            // Only forward RESPONSEs from our target device
            if (message.getSourceAddress().getValue() == cfg.address) {
                Bridge bridge = getBridge();
                if (bridge != null) {
                    ThingHandler handler = bridge.getHandler();
                    if (handler instanceof IDSMyRVBridgeHandler bridgeHandler) {
                        if (message.getTargetAddress().equals(bridgeHandler.getSourceAddress())) {
                            logger.debug("🔄 Forwarding RESPONSE to SessionManager: from device {}",
                                    message.getSourceAddress().getValue());
                            sm.processResponse(message);
                        }
                    }
                }
            }
        }

        // Check if this is a status update from our device
        if (message.getMessageType() == MessageType.DEVICE_STATUS
                && message.getSourceAddress().getValue() == cfg.address) {
            if (waitingForStatus) {
                // Command completed, cancel timeout
                ScheduledFuture<?> task = commandTimeoutTask;
                if (task != null) {
                    task.cancel(false);
                }
                waitingForStatus = false;
            }

            // Update state from status message
            updateRelayStatus(message.getData());
        }
    }

    /**
     * Update relay status from device status message.
     * Supports both Type 1 (1 byte) and Type 2 (6+ bytes) status formats.
     */
    private void updateRelayStatus(byte[] data) {
        if (data.length < 1) {
            logger.debug("Relay status message too short");
            return;
        }

        DeviceType deviceType = getDeviceType();
        boolean isType2 = (deviceType == DeviceType.LATCHING_RELAY_TYPE_2) || (data.length >= 6);

        if (isType2 && data.length >= 6) {
            // Type 2 status format (from C# LogicalDeviceRelayStatusType2):
            // Byte 0, bits 0-3 (0x0F): RawOutputState (0=Off, 1=On, other=Unknown)
            // Byte 0, bit 5 (0x20): OutputDisabledLatchBit
            // Byte 1: Position (0-100, or 255 if unknown)
            // Bytes 2-3: CurrentDrawAmps (fixed point 8.8, big-endian, 0xFFFF = not supported)
            // Bytes 4-5: DTC_ID (uint16, big-endian, 0 = UNKNOWN)

            // Parse state: lower 4 bits, 0=Off, 1=On, other=Unknown
            // From C#: State == RelayBasicOutputState.On (which is 1)
            int rawOutputState = data[0] & 0x0F;
            boolean newState = (rawOutputState == 1); // Only 1 means ON
            boolean outputDisabled = (data[0] & 0x20) != 0; // Bit 5
            int position = data[1] & 0xFF;

            // Current draw: fixed point 8.8 format (big-endian)
            // From C#: FixedPointUnsignedBigEndian8X8.ToFloat() = (float)(int)fixedPointNumber / 256f
            int currentRaw = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
            float currentDrawAmps = currentRaw / 256.0f;
            // Check if current is supported (0xFFFF = not supported)
            boolean currentSupported = (currentRaw != 0xFFFF);

            // DTC reason: uint16 big-endian (0 = UNKNOWN)
            int dtcReason = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
            // From C#: IsFaulted returns true only if OutputDisabled && OutputDisabledDtcReason != DTC_ID.UNKNOWN
            boolean faulted = outputDisabled && (dtcReason != 0);

            // Track position changes for conditional updates
            lastPosition = position; // Update tracked position

            // Update switch state
            if (newState != isOn) {
                isOn = newState;
                updateState(IDSMyRVBindingConstants.CHANNEL_RELAY_SWITCH, isOn ? OnOffType.ON : OnOffType.OFF);
            }

            // Update fault status (always update to ensure it's set)
            updateState(IDSMyRVBindingConstants.CHANNEL_RELAY_FAULT, faulted ? OnOffType.ON : OnOffType.OFF);

            // Update output disabled status (always update to ensure it's set)
            updateState(IDSMyRVBindingConstants.CHANNEL_RELAY_OUTPUT_DISABLED,
                    outputDisabled ? OnOffType.ON : OnOffType.OFF);

            // Update position only if device supports it
            if (isPositionSupported()) {
                // Update position (always update, even if value hasn't changed)
                // From C#: IsPositionKnown returns true if Position <= 100
                if (position <= 100) {
                    updateState(IDSMyRVBindingConstants.CHANNEL_RELAY_POSITION, new DecimalType(position));
                } else {
                    // Position unknown (255 or > 100), set to 255 to indicate unknown
                    updateState(IDSMyRVBindingConstants.CHANNEL_RELAY_POSITION, new DecimalType(255));
                }
            }

            // Update current draw (always update, but use 0 if not supported)
            if (currentSupported) {
                updateState(IDSMyRVBindingConstants.CHANNEL_RELAY_CURRENT_DRAW, new DecimalType(currentDrawAmps));
            } else {
                // Current not supported, set to 0
                updateState(IDSMyRVBindingConstants.CHANNEL_RELAY_CURRENT_DRAW, new DecimalType(0));
            }

            // Update DTC reason (always update)
            updateState(IDSMyRVBindingConstants.CHANNEL_RELAY_DTC_REASON, new DecimalType(dtcReason));

            DeviceConfiguration cfg = config;
            logger.debug(
                    "Relay {} status updated (Type 2): state={} (raw={}), faulted={}, outputDisabled={}, position={}, current={}A (raw=0x{}), dtc={}",
                    cfg != null ? cfg.address : "?", isOn ? "ON" : "OFF", rawOutputState, faulted, outputDisabled,
                    position, currentSupported ? String.format("%.2f", currentDrawAmps) : "N/A",
                    String.format("%04X", currentRaw), dtcReason);
        } else {
            // Type 1 status format (from C# LogicalDeviceRelayBasicStatusType1):
            // Bit 0 (0x01): RelayStateBit (ON/OFF)
            // Bit 6 (0x40): FaultBit (faulted/disabled)
            boolean newState = (data[0] & 0x01) != 0;
            boolean faulted = (data[0] & 0x40) != 0;
            boolean outputDisabled = faulted; // Type 1: OutputDisabled == IsFaulted

            // Update switch state
            if (newState != isOn) {
                isOn = newState;
                updateState(IDSMyRVBindingConstants.CHANNEL_RELAY_SWITCH, isOn ? OnOffType.ON : OnOffType.OFF);
            }

            // Update fault status
            updateState(IDSMyRVBindingConstants.CHANNEL_RELAY_FAULT, faulted ? OnOffType.ON : OnOffType.OFF);

            // Update output disabled status
            updateState(IDSMyRVBindingConstants.CHANNEL_RELAY_OUTPUT_DISABLED,
                    outputDisabled ? OnOffType.ON : OnOffType.OFF);

            // Type 1 doesn't support position, current, or DTC
            // Position: not supported (255)
            updateState(IDSMyRVBindingConstants.CHANNEL_RELAY_POSITION, new DecimalType(255));

            // Current: not supported (0)
            updateState(IDSMyRVBindingConstants.CHANNEL_RELAY_CURRENT_DRAW, new DecimalType(0));

            // DTC: UNKNOWN (0)
            updateState(IDSMyRVBindingConstants.CHANNEL_RELAY_DTC_REASON, new DecimalType(0));

            DeviceConfiguration cfg = config;
            logger.debug("Relay {} status updated (Type 1): state={}, faulted={}", cfg != null ? cfg.address : "?",
                    isOn ? "ON" : "OFF", faulted);
        }
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> task = commandTimeoutTask;
        if (task != null) {
            task.cancel(false);
            commandTimeoutTask = null;
        }

        super.dispose();
    }
}
