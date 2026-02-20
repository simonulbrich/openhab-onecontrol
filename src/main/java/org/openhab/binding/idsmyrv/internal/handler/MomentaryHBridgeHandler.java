package org.openhab.binding.idsmyrv.internal.handler;

import static org.openhab.binding.idsmyrv.internal.IDSMyRVBindingConstants.*;

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
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MomentaryHBridgeHandler} handles momentary H-Bridge motor control devices.
 * H-Bridge devices control motors in forward, reverse, or stop directions.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class MomentaryHBridgeHandler extends BaseIDSMyRVDeviceHandler {

    @SuppressWarnings("null")
    private final Logger logger = LoggerFactory.getLogger(MomentaryHBridgeHandler.class);

    private @Nullable DeviceConfiguration config;
    private @Nullable ScheduledFuture<?> commandTimeoutTask;
    private @Nullable ScheduledFuture<?> repeatingCommandTask; // Task that repeats the command while button is held
    private @Nullable ScheduledFuture<?> autoStopTask; // Task that auto-stops if no command received within timeout

    private String currentDirection = "STOP"; // STOP, FORWARD, REVERSE
    private String activeCommandDirection = "STOP"; // The direction we're currently sending commands for
    private boolean waitingForStatus = false;
    private int lastPosition = -1; // Track last position value for change detection

    // Command repeat interval - send command every 500ms while button is held
    private static final long COMMAND_REPEAT_INTERVAL_MS = 500;
    // Auto-stop timeout - if no command received within this time, stop the motor
    // This handles the case where OpenHAB sends command on button release
    private static final long AUTO_STOP_TIMEOUT_MS = 200;

    public MomentaryHBridgeHandler(Thing thing) {
        super(thing);
    }

    @Override
    protected void initializeDevice() {
        logger.debug("Initializing Momentary H-Bridge Handler for thing {}", getThing().getUID());

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
     * Defaults to MOMENTARY_H_BRIDGE (Type 1) if not specified.
     */
    private DeviceType getDeviceType() {
        Object deviceTypeValue = getThing().getProperties().get(PROPERTY_DEVICE_TYPE);
        if (deviceTypeValue instanceof Integer) {
            return DeviceType.fromValue((Integer) deviceTypeValue);
        } else if (deviceTypeValue instanceof String) {
            try {
                return DeviceType.fromValue(Integer.parseInt((String) deviceTypeValue));
            } catch (NumberFormatException e) {
                logger.debug("Could not parse device type property: {}", deviceTypeValue);
            }
        }
        // Default to Type 1
        return DeviceType.MOMENTARY_H_BRIDGE;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            // Refresh - could request status update, but for now just return
            logger.debug("Refresh command received for channel {}", channelUID.getId());
            return;
        }

        DeviceConfiguration cfg = config;
        if (cfg == null) {
            logger.warn("Configuration not available, cannot handle command");
            return;
        }

        String channelId = channelUID.getId();

        try {
            if (CHANNEL_H_BRIDGE_DIRECTION.equals(channelId)) {
                if (command instanceof UpDownType) {
                    // UP = FORWARD, DOWN = REVERSE
                    // These commands should be sent continuously while the button is held
                    if (command == UpDownType.UP) {
                        handleDirectionCommand("FORWARD");
                    } else if (command == UpDownType.DOWN) {
                        handleDirectionCommand("REVERSE");
                    }
                } else if (command instanceof StopMoveType) {
                    // STOP command - button released, stop repeating and send STOP
                    if (command == StopMoveType.STOP) {
                        handleDirectionCommand("STOP");
                    } else if (command == StopMoveType.MOVE) {
                        // MOVE is typically used for continuous movement, but for H-Bridge
                        // we'll treat it as a request to continue current direction if any
                        logger.debug("MOVE command received, maintaining current direction if active");
                    }
                } else {
                    logger.warn("Invalid command type for direction channel: {}", command.getClass().getSimpleName());
                }
            } else {
                logger.debug("Unhandled channel: {}", channelId);
            }
        } catch (Exception e) {
            logger.warn("Error handling command for channel {}: {}", channelId, e.getMessage());
        }
    }

    /**
     * Handle a direction command (FORWARD, REVERSE, STOP).
     * For FORWARD/REVERSE: Starts repeating the command while button is held.
     * For STOP: Stops repeating and sends a single STOP command.
     * 
     * This method ensures that FORWARD/REVERSE commands are sent continuously
     * at regular intervals (every 500ms) while the button is held, matching
     * the behavior expected by the C# implementation which auto-stops if no
     * command is received within 800ms.
     * 
     * To handle OpenHAB's behavior where commands may be sent on button release
     * rather than continuously while held, we use an auto-stop timeout: if no
     * new command is received within AUTO_STOP_TIMEOUT_MS, we automatically stop.
     */
    private void handleDirectionCommand(String direction) throws Exception {
        String directionUpper = direction.toUpperCase();

        // If STOP is received, cancel any repeating command and send STOP
        if ("STOP".equals(directionUpper)) {
            stopRepeatingCommand();
            cancelAutoStopTask();
            activeCommandDirection = "STOP";
            sendSingleCommand("STOP");
            return;
        }

        // If FORWARD or REVERSE is received, start/continue repeating the command
        if ("FORWARD".equals(directionUpper) || "REVERSE".equals(directionUpper)) {
            // Cancel any pending auto-stop task since we received a new command
            cancelAutoStopTask();

            // If we're already sending this direction, reset the timer to ensure
            // we keep sending commands (in case OpenHAB sends repeated commands)
            if (directionUpper.equals(activeCommandDirection)) {
                logger.debug("Already sending {} command, resetting auto-stop timer...", directionUpper);
                // Send command immediately to reset the device's timeout timer
                sendSingleCommand(directionUpper);
                // Schedule auto-stop in case this was the last command (button released)
                scheduleAutoStop();
                return;
            }

            // Stop any existing repeating command (switching direction)
            stopRepeatingCommand();

            // Start repeating the new direction
            activeCommandDirection = directionUpper;
            startRepeatingCommand(directionUpper);
            
            // Schedule auto-stop in case OpenHAB only sends one command (on release)
            scheduleAutoStop();
        } else {
            logger.warn("Invalid direction command: {}", direction);
        }
    }

    /**
     * Start repeating a command (FORWARD or REVERSE) at regular intervals.
     * 
     * Commands are sent every 500ms to ensure the device doesn't auto-stop
     * (the C# implementation auto-stops if no command is received within 800ms).
     */
    private void startRepeatingCommand(String direction) {
        DeviceConfiguration cfg = config;
        if (cfg == null) {
            logger.warn("Cannot start repeating command: configuration not available");
            return;
        }

        // Send the first command immediately
        try {
            sendSingleCommand(direction);
            logger.info("Started continuous {} command (sending every {}ms)", direction, COMMAND_REPEAT_INTERVAL_MS);
        } catch (Exception e) {
            logger.warn("Failed to send initial {} command: {}", direction, e.getMessage());
            activeCommandDirection = "STOP";
            return;
        }

        // Schedule repeating task to send command every COMMAND_REPEAT_INTERVAL_MS
        // This ensures the device receives commands continuously while the button is held
        repeatingCommandTask = scheduler.scheduleAtFixedRate(() -> {
            // Check if we should still be sending this direction
            if (!direction.equals(activeCommandDirection)) {
                // Direction changed, stop this task
                stopRepeatingCommand();
                return;
            }

            try {
                sendSingleCommand(direction);
            } catch (Exception e) {
                logger.warn("Failed to send repeating {} command: {}", direction, e.getMessage());
                // Stop repeating on error
                stopRepeatingCommand();
            }
        }, COMMAND_REPEAT_INTERVAL_MS, COMMAND_REPEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Stop the repeating command task and reset state.
     * Called when STOP command is received or when switching directions.
     */
    private void stopRepeatingCommand() {
        ScheduledFuture<?> task = repeatingCommandTask;
        if (task != null) {
            task.cancel(false);
            repeatingCommandTask = null;
            logger.debug("Stopped repeating command task");
        }
        // Don't reset activeCommandDirection here - let the caller set it appropriately
        // (either to "STOP" or to the new direction)
    }

    /**
     * Schedule an auto-stop task that will stop the motor if no new command
     * is received within AUTO_STOP_TIMEOUT_MS. This handles the case where
     * OpenHAB sends commands on button release rather than continuously.
     */
    private void scheduleAutoStop() {
        // Cancel any existing auto-stop task
        cancelAutoStopTask();

        // Schedule new auto-stop task
        autoStopTask = scheduler.schedule(() -> {
            // If we're still sending a direction command, stop it
            if (!"STOP".equals(activeCommandDirection)) {
                logger.debug("Auto-stopping motor - no command received within {}ms (button likely released)", AUTO_STOP_TIMEOUT_MS);
                try {
                    stopRepeatingCommand();
                    activeCommandDirection = "STOP";
                    sendSingleCommand("STOP");
                } catch (Exception e) {
                    logger.warn("Error during auto-stop: {}", e.getMessage());
                }
            }
            autoStopTask = null;
        }, AUTO_STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Cancel the auto-stop task if it exists.
     */
    private void cancelAutoStopTask() {
        ScheduledFuture<?> task = autoStopTask;
        if (task != null) {
            task.cancel(false);
            autoStopTask = null;
        }
    }

    /**
     * Send a single command to the H-Bridge device.
     */
    private void sendSingleCommand(String direction) throws Exception {
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

        // Build command based on device type
        DeviceType deviceType = getDeviceType();
        CommandBuilder builder = new CommandBuilder(bridgeHandler.getSourceAddress(), targetAddress);
        CANMessage message;

        String directionUpper = direction.toUpperCase();
        boolean isType2 = (deviceType == DeviceType.MOMENTARY_H_BRIDGE_T2);

        switch (directionUpper) {
            case "FORWARD":
                if (isType2) {
                    message = builder.setHBridgeForwardType2();
                } else {
                    message = builder.setHBridgeForwardType1();
                }
                currentDirection = "FORWARD";
                logger.debug("▶️ Sending FORWARD command to H-Bridge {} (type: {})", cfg.address, deviceType.getName());
                break;

            case "REVERSE":
                if (isType2) {
                    message = builder.setHBridgeReverseType2();
                } else {
                    message = builder.setHBridgeReverseType1();
                }
                currentDirection = "REVERSE";
                logger.debug("◀️ Sending REVERSE command to H-Bridge {} (type: {})", cfg.address, deviceType.getName());
                break;

            case "STOP":
                if (isType2) {
                    message = builder.setHBridgeStopType2();
                } else {
                    message = builder.setHBridgeStopType1();
                }
                currentDirection = "STOP";
                logger.info("⏹️ Sending STOP command to H-Bridge {} (type: {})", cfg.address, deviceType.getName());
                break;

            default:
                throw new Exception("Invalid direction command: " + direction);
        }

        // Send command
        bridgeHandler.sendMessage(message);
        waitingForStatus = true;

        // Set a timeout for status update (only for STOP, since FORWARD/REVERSE will keep repeating)
        if ("STOP".equals(directionUpper)) {
            scheduleCommandTimeout();
        }
    }

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
                IDSMyRVBridgeHandler bridgeHandler = getBridgeHandler();
                if (bridgeHandler != null && message.getTargetAddress().equals(bridgeHandler.getSourceAddress())) {
                    logger.debug("🔄 Forwarding RESPONSE to SessionManager: from device {}",
                            message.getSourceAddress().getValue());
                    sm.processResponse(message);
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
            updateHBridgeStatus(message.getData());
        }
    }

    /**
     * Update H-Bridge status from device status message.
     * Supports both Type 1 (1 byte) and Type 2 (6+ bytes) status formats.
     */
    private void updateHBridgeStatus(byte[] data) {
        if (data.length < 1) {
            logger.debug("H-Bridge status message too short");
            return;
        }

        DeviceType deviceType = getDeviceType();
        boolean isType2 = (deviceType == DeviceType.MOMENTARY_H_BRIDGE_T2) || (data.length >= 6);

        if (isType2 && data.length >= 6) {
            // Type 2 status format (from C# LogicalDeviceRelayHBridgeStatusType2):
            // Byte 0, bits 0-3 (0x0F): RawOutputState (0=Stop, 1=Forward, 2=Reverse, other=Unknown)
            // Byte 0, bit 5 (0x20): OutputDisabledLatchBit
            // Byte 0, bit 6 (0x40): ReverseCommandAllowedBitmask
            // Byte 0, bit 7 (0x80): ForwardCommandAllowedBitmask
            // Byte 1: Position (0-100, or 255 if unknown)
            // Bytes 2-3: CurrentDrawAmps (fixed point 8.8, big-endian, 0xFFFF = not supported)
            // Bytes 4-5: DTC_ID (uint16, big-endian, 0 = UNKNOWN)

            int rawOutputState = data[0] & 0x0F;
            boolean outputDisabled = (data[0] & 0x20) != 0;
            // Note: forwardAllowed and reverseAllowed bits are available but not currently used
            // They indicate whether forward/reverse commands are allowed (not hazardous)

            // Update direction
            String newDirection;
            switch (rawOutputState) {
                case 0:
                    newDirection = "STOP";
                    break;
                case 1:
                    newDirection = "FORWARD";
                    break;
                case 2:
                    newDirection = "REVERSE";
                    break;
                default:
                    newDirection = "UNKNOWN";
                    break;
            }

            if (!newDirection.equals(currentDirection)) {
                currentDirection = newDirection;
                
                // Update Rollershutter channel state
                if (newDirection.equals("FORWARD")) {
                    updateState(new ChannelUID(getThing().getUID(), CHANNEL_H_BRIDGE_DIRECTION), UpDownType.UP);
                } else if (newDirection.equals("REVERSE")) {
                    updateState(new ChannelUID(getThing().getUID(), CHANNEL_H_BRIDGE_DIRECTION), UpDownType.DOWN);
                } else {
                    // STOP - use position if available, otherwise use 50% as neutral
                    int position = data[1] & 0xFF;
                    if (position == 255) {
                        position = 50; // Unknown position, use neutral
                    }
                    updateState(new ChannelUID(getThing().getUID(), CHANNEL_H_BRIDGE_DIRECTION),
                            new org.openhab.core.library.types.PercentType(position));
                }
                logger.debug("H-Bridge direction updated: {}", newDirection);
            }

            // Update fault/output disabled
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_H_BRIDGE_FAULT),
                    outputDisabled ? org.openhab.core.library.types.OnOffType.ON
                            : org.openhab.core.library.types.OnOffType.OFF);
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_H_BRIDGE_OUTPUT_DISABLED),
                    outputDisabled ? org.openhab.core.library.types.OnOffType.ON
                            : org.openhab.core.library.types.OnOffType.OFF);

            // Update position (if available)
            int position = data[1] & 0xFF;
            if (position != 255 && position != lastPosition) {
                lastPosition = position;
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_H_BRIDGE_POSITION),
                        new org.openhab.core.library.types.DecimalType(position));
                
                // Also update the rollershutter channel with position if motor is stopped
                if (newDirection.equals("STOP")) {
                    updateState(new ChannelUID(getThing().getUID(), CHANNEL_H_BRIDGE_DIRECTION),
                            new org.openhab.core.library.types.PercentType(position));
                }
                logger.debug("H-Bridge position updated: {}%", position);
            }

            // Update current draw (if available)
            int currentRaw = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
            if (currentRaw != 0xFFFF) {
                // Fixed point 8.8 format: divide by 256
                float currentAmps = currentRaw / 256.0f;
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_H_BRIDGE_CURRENT_DRAW),
                        new org.openhab.core.library.types.DecimalType(currentAmps));
            }

            // Update DTC reason
            int dtcId = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
            if (dtcId != 0) {
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_H_BRIDGE_DTC_REASON),
                        new org.openhab.core.library.types.DecimalType(dtcId));
            }

        } else {
            // Type 1 status format (from C# LogicalDeviceRelayHBridgeStatusType1):
            // Byte 0, bit 0 (0x01): Relay1State (Forward)
            // Byte 0, bit 1 (0x02): Relay2State (Reverse)
            // Byte 0, bit 6 (0x40): FaultBit

            boolean relay1State = (data[0] & 0x01) != 0;
            boolean relay2State = (data[0] & 0x02) != 0;
            boolean fault = (data[0] & 0x40) != 0;

            // Determine direction from relay states
            String newDirection;
            if (!relay1State && !relay2State) {
                newDirection = "STOP";
            } else if (relay1State && !relay2State) {
                newDirection = "FORWARD";
            } else if (!relay1State && relay2State) {
                newDirection = "REVERSE";
            } else {
                // Both relays on (shouldn't happen, but handle it)
                newDirection = "UNKNOWN";
                logger.warn("H-Bridge has both relays on (invalid state)");
            }

            if (!newDirection.equals(currentDirection)) {
                currentDirection = newDirection;
                
                // Update Rollershutter channel state
                if (newDirection.equals("FORWARD")) {
                    updateState(new ChannelUID(getThing().getUID(), CHANNEL_H_BRIDGE_DIRECTION), UpDownType.UP);
                } else if (newDirection.equals("REVERSE")) {
                    updateState(new ChannelUID(getThing().getUID(), CHANNEL_H_BRIDGE_DIRECTION), UpDownType.DOWN);
                } else {
                    // STOP or UNKNOWN - use 50% as neutral position for momentary motors
                    updateState(new ChannelUID(getThing().getUID(), CHANNEL_H_BRIDGE_DIRECTION),
                            new org.openhab.core.library.types.PercentType(50));
                }
                logger.debug("H-Bridge direction updated: {}", newDirection);
            }

            // Update fault
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_H_BRIDGE_FAULT),
                    fault ? org.openhab.core.library.types.OnOffType.ON
                            : org.openhab.core.library.types.OnOffType.OFF);
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_H_BRIDGE_OUTPUT_DISABLED),
                    fault ? org.openhab.core.library.types.OnOffType.ON
                            : org.openhab.core.library.types.OnOffType.OFF);
        }
    }

    @Override
    public void dispose() {
        // Cancel all scheduled tasks
        ScheduledFuture<?> task = commandTimeoutTask;
        if (task != null) {
            task.cancel(true);
            commandTimeoutTask = null;
        }

        stopRepeatingCommand();
        cancelAutoStopTask();

        // Call parent dispose to clean up session manager
        super.dispose();
    }
}

