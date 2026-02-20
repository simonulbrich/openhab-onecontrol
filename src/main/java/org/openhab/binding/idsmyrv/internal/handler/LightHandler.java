package org.openhab.binding.idsmyrv.internal.handler;

import static org.openhab.binding.idsmyrv.internal.IDSMyRVBindingConstants.*;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;
import org.openhab.binding.idsmyrv.internal.config.LightConfiguration;
import org.openhab.binding.idsmyrv.internal.idscan.CommandBuilder;
import org.openhab.binding.idsmyrv.internal.idscan.IDSMessage;
import org.openhab.binding.idsmyrv.internal.idscan.MessageType;
import org.openhab.binding.idsmyrv.internal.idscan.SessionManager;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StringType;
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
 * The {@link LightHandler} handles commands for dimmable lights.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class LightHandler extends BaseIDSMyRVDeviceHandler {

    private @Nullable LightConfiguration config;
    private @Nullable ScheduledFuture<?> commandTimeoutTask;

    private boolean isOn = false;
    private int brightness = 100;
    private String mode = "OFF";
    private int sleepTime = 0;
    private int cycleTime1 = 0;
    private int cycleTime2 = 0;
    private boolean waitingForStatus = false;

    public LightHandler(Thing thing) {
        super(thing);
    }

    @Override
    protected void initializeDevice() {
        logger.debug("Initializing Light Handler for thing {}", getThing().getUID());

        config = getConfigAs(LightConfiguration.class);

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
        LightConfiguration cfg = config;
        return new Address(cfg != null ? cfg.address : 0);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            // Refresh state - we'll update from STATUS messages
            return;
        }

        String channelId = channelUID.getId();

        try {
            if (CHANNEL_SWITCH.equals(channelId)) {
                if (command instanceof OnOffType onOffCommand) {
                    handleSwitchCommand(onOffCommand);
                }
            } else if (CHANNEL_BRIGHTNESS.equals(channelId)) {
                if (command instanceof PercentType percentCommand) {
                    handleBrightnessCommand(percentCommand);
                }
            } else if (CHANNEL_MODE.equals(channelId)) {
                if (command instanceof StringType stringCommand) {
                    handleModeCommand(stringCommand);
                }
            } else if (CHANNEL_SLEEP.equals(channelId)) {
                if (command instanceof DecimalType decimalCommand) {
                    handleSleepCommand(decimalCommand);
                }
            } else if (CHANNEL_TIME1.equals(channelId)) {
                if (command instanceof DecimalType decimalCommand) {
                    handleCycleTime1Command(decimalCommand);
                }
            } else if (CHANNEL_TIME2.equals(channelId)) {
                if (command instanceof DecimalType decimalCommand) {
                    handleCycleTime2Command(decimalCommand);
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
        LightConfiguration cfg = config;
        if (cfg == null) {
            return;
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
        CANMessage message;
        if (command == OnOffType.ON) {
            // TEMPORARY: Always use 100% brightness for testing
            message = builder.setLightOn(100);
            brightness = 100;
            isOn = true;
            logger.info("💡 Sending ON command with 100% brightness (0xFF) to device {}", cfg.address);
        } else {
            // TEMPORARY: Preserve 100% brightness when turning off
            message = builder.setLightOff(100);
            isOn = false;
            logger.info("💡 Sending OFF command (preserving 100% brightness) to device {}", cfg.address);
        }

        // Send command
        bridgeHandler.sendMessage(message);
        waitingForStatus = true;

        // Set a timeout for status update
        scheduleCommandTimeout();

        logger.debug("Sent {} command to light {}", command, cfg.address);
    }

    /**
     * Handle a mode command (OFF, DIMMER, BLINK, SWELL).
     */
    private void handleModeCommand(StringType command) throws Exception {
        LightConfiguration cfg = config;
        if (cfg == null) {
            return;
        }

        String modeValue = command.toString().toUpperCase();
        int modeInt;
        switch (modeValue) {
            case "OFF":
                modeInt = 0;
                break;
            case "DIMMER":
            case "ON":
                modeInt = 1;
                break;
            case "BLINK":
                modeInt = 2;
                break;
            case "SWELL":
                modeInt = 3;
                break;
            default:
                logger.warn("Invalid mode: {}", modeValue);
                return;
        }

        Address targetAddress = new Address(cfg.address);
        IDSMyRVBridgeHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler == null) {
            throw new Exception("Bridge handler not available");
        }

        // Ensure session is open
        ensureSession(targetAddress, bridgeHandler);

        // Get current brightness (scale from 0-100 to 0-255 for maxBrightness)
        int maxBrightness = brightness;

        // Build command with current values for other parameters
        CommandBuilder builder = new CommandBuilder(bridgeHandler.getSourceAddress(), targetAddress);
        CANMessage message = builder.setLightCommand(modeInt, maxBrightness, sleepTime, cycleTime1, cycleTime2);

        // Send command
        bridgeHandler.sendMessage(message);
        waitingForStatus = true;
        scheduleCommandTimeout();

        logger.info("💡 Sending mode command: {} to light {}", modeValue, cfg.address);
    }

    /**
     * Handle a sleep time command.
     */
    private void handleSleepCommand(DecimalType command) throws Exception {
        LightConfiguration cfg = config;
        if (cfg == null) {
            return;
        }

        int newSleepTime = command.intValue();
        if (newSleepTime < 0 || newSleepTime > 255) {
            logger.warn("Sleep time out of range: {} (must be 0-255)", newSleepTime);
            return;
        }

        Address targetAddress = new Address(cfg.address);
        IDSMyRVBridgeHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler == null) {
            throw new Exception("Bridge handler not available");
        }

        // Ensure session is open
        ensureSession(targetAddress, bridgeHandler);

        // Get current mode (convert from string to int)
        int modeInt;
        switch (mode) {
            case "OFF":
                modeInt = 0;
                break;
            case "DIMMER":
                modeInt = 1;
                break;
            case "BLINK":
                modeInt = 2;
                break;
            case "SWELL":
                modeInt = 3;
                break;
            default:
                modeInt = 0;
                break;
        }

        // Build command with new sleep time, keeping other values
        CommandBuilder builder = new CommandBuilder(bridgeHandler.getSourceAddress(), targetAddress);
        CANMessage message = builder.setLightCommand(modeInt, brightness, newSleepTime, cycleTime1, cycleTime2);

        // Send command
        bridgeHandler.sendMessage(message);
        waitingForStatus = true;
        scheduleCommandTimeout();

        logger.info("💡 Sending sleep time command: {}s to light {}", newSleepTime, cfg.address);
    }

    /**
     * Handle a cycle time 1 command.
     */
    private void handleCycleTime1Command(DecimalType command) throws Exception {
        LightConfiguration cfg = config;
        if (cfg == null) {
            return;
        }

        int newCycleTime1 = command.intValue();
        if (newCycleTime1 < 0 || newCycleTime1 > 65535) {
            logger.warn("Cycle time 1 out of range: {} (must be 0-65535)", newCycleTime1);
            return;
        }

        Address targetAddress = new Address(cfg.address);
        IDSMyRVBridgeHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler == null) {
            throw new Exception("Bridge handler not available");
        }

        // Ensure session is open
        ensureSession(targetAddress, bridgeHandler);

        // Get current mode
        int modeInt;
        switch (mode) {
            case "OFF":
                modeInt = 0;
                break;
            case "DIMMER":
                modeInt = 1;
                break;
            case "BLINK":
                modeInt = 2;
                break;
            case "SWELL":
                modeInt = 3;
                break;
            default:
                modeInt = 0;
                break;
        }

        // Build command with new cycle time 1, keeping other values
        CommandBuilder builder = new CommandBuilder(bridgeHandler.getSourceAddress(), targetAddress);
        CANMessage message = builder.setLightCommand(modeInt, brightness, sleepTime, newCycleTime1, cycleTime2);

        // Send command
        bridgeHandler.sendMessage(message);
        waitingForStatus = true;
        scheduleCommandTimeout();

        logger.info("💡 Sending cycle time 1 command: {}ms to light {}", newCycleTime1, cfg.address);
    }

    /**
     * Handle a cycle time 2 command.
     */
    private void handleCycleTime2Command(DecimalType command) throws Exception {
        LightConfiguration cfg = config;
        if (cfg == null) {
            return;
        }

        int newCycleTime2 = command.intValue();
        if (newCycleTime2 < 0 || newCycleTime2 > 65535) {
            logger.warn("Cycle time 2 out of range: {} (must be 0-65535)", newCycleTime2);
            return;
        }

        Address targetAddress = new Address(cfg.address);
        IDSMyRVBridgeHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler == null) {
            throw new Exception("Bridge handler not available");
        }

        // Ensure session is open
        ensureSession(targetAddress, bridgeHandler);

        // Get current mode
        int modeInt;
        switch (mode) {
            case "OFF":
                modeInt = 0;
                break;
            case "DIMMER":
                modeInt = 1;
                break;
            case "BLINK":
                modeInt = 2;
                break;
            case "SWELL":
                modeInt = 3;
                break;
            default:
                modeInt = 0;
                break;
        }

        // Build command with new cycle time 2, keeping other values
        CommandBuilder builder = new CommandBuilder(bridgeHandler.getSourceAddress(), targetAddress);
        CANMessage message = builder.setLightCommand(modeInt, brightness, sleepTime, cycleTime1, newCycleTime2);

        // Send command
        bridgeHandler.sendMessage(message);
        waitingForStatus = true;
        scheduleCommandTimeout();

        logger.info("💡 Sending cycle time 2 command: {}ms to light {}", newCycleTime2, cfg.address);
    }

    /**
     * Handle a brightness command.
     */
    private void handleBrightnessCommand(PercentType command) throws Exception {
        LightConfiguration cfg = config;
        if (cfg == null) {
            return;
        }

        Address targetAddress = new Address(cfg.address);
        IDSMyRVBridgeHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler == null) {
            throw new Exception("Bridge handler not available");
        }

        // Update brightness
        brightness = command.intValue();

        // If brightness > 0, turn on with new brightness
        // If brightness == 0, turn off
        if (brightness > 0) {
            // Ensure session is open
            ensureSession(targetAddress, bridgeHandler);

            CommandBuilder builder = new CommandBuilder(bridgeHandler.getSourceAddress(), targetAddress);
            CANMessage message = builder.setLightOn(brightness);

            bridgeHandler.sendMessage(message);
            isOn = true;
            waitingForStatus = true;

            scheduleCommandTimeout();

            logger.debug("Sent brightness {} command to light {}", brightness, cfg.address);
        } else {
            // Brightness 0 means OFF
            handleSwitchCommand(OnOffType.OFF);
        }
    }

    /**
     * Ensure a session is open with the target device.
     */
    // ensureSession() is now inherited from BaseIDSMyRVDeviceHandler

    /**
     * Schedule a timeout for command execution.
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
        LightConfiguration cfg = config;
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
                    logger.debug("🔄 Forwarding RESPONSE to SessionManager: from device {}, msgData=0x{}",
                            message.getSourceAddress().getValue(), String.format("%02X", message.getMessageData()));
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
            // Dimmable light status format (8 bytes):
            // Byte 0: Mode (0=OFF, 1=ON/DIMMER, 2=BLINK, 3=SWELL)
            // Byte 1: MaxBrightness
            // Byte 2: Duration (sleep time, 0-255)
            // Byte 3: CurrentBrightness
            // Bytes 4-5: CycleTime1 (uint16, big-endian)
            // Bytes 6-7: CycleTime2 (uint16, big-endian)
            byte[] payload = message.getData();
            if (payload.length >= 8) {
                // Full 8-byte status message
                int modeRaw = payload[0] & 0xFF;
                // Byte 1: MaxBrightness (not currently used, but part of status)
                int duration = payload[2] & 0xFF;
                int currentBrightnessRaw = payload[3] & 0xFF;
                int cycleTime1Raw = ((payload[4] & 0xFF) << 8) | (payload[5] & 0xFF);
                int cycleTime2Raw = ((payload[6] & 0xFF) << 8) | (payload[7] & 0xFF);

                // Parse mode
                String newMode;
                switch (modeRaw) {
                    case 0:
                        newMode = "OFF";
                        break;
                    case 1:
                        newMode = "DIMMER";
                        break;
                    case 2:
                        newMode = "BLINK";
                        break;
                    case 3:
                        newMode = "SWELL";
                        break;
                    default:
                        newMode = "OFF";
                        break;
                }

                // Mode > 0 means light is on (ON, BLINK, SWELL)
                boolean newIsOn = (modeRaw > 0);

                // Update switch state
                if (newIsOn != isOn) {
                    isOn = newIsOn;
                    updateState(CHANNEL_SWITCH, isOn ? OnOffType.ON : OnOffType.OFF);
                }

                // Update mode
                if (!newMode.equals(mode)) {
                    mode = newMode;
                    updateState(CHANNEL_MODE, new StringType(mode));
                }

                // Update brightness from byte 3 (CurrentBrightness)
                // Scale from 0-255 to 0-100
                int newBrightness = (currentBrightnessRaw * 100) / 255;
                if (newBrightness != brightness) {
                    brightness = newBrightness;
                    updateState(CHANNEL_BRIGHTNESS, new PercentType(brightness));
                }

                // Update sleep time (duration)
                if (duration != sleepTime) {
                    sleepTime = duration;
                    updateState(CHANNEL_SLEEP, new DecimalType(sleepTime));
                }

                // Update cycle times
                if (cycleTime1Raw != cycleTime1) {
                    cycleTime1 = cycleTime1Raw;
                    updateState(CHANNEL_TIME1, new DecimalType(cycleTime1));
                }

                if (cycleTime2Raw != cycleTime2) {
                    cycleTime2 = cycleTime2Raw;
                    updateState(CHANNEL_TIME2, new DecimalType(cycleTime2));
                }

                logger.debug("Light {} status updated: mode={}, brightness={}%, sleep={}s, time1={}ms, time2={}ms",
                        cfg.address, mode, brightness, sleepTime, cycleTime1, cycleTime2);
            } else if (payload.length >= 4) {
                // Partial status message (at least 4 bytes)
                int modeRaw = payload[0] & 0xFF;
                int currentBrightnessRaw = payload[3] & 0xFF;

                // Parse mode
                String newMode;
                switch (modeRaw) {
                    case 0:
                        newMode = "OFF";
                        break;
                    case 1:
                        newMode = "DIMMER";
                        break;
                    case 2:
                        newMode = "BLINK";
                        break;
                    case 3:
                        newMode = "SWELL";
                        break;
                    default:
                        newMode = "OFF";
                        break;
                }

                boolean newIsOn = (modeRaw > 0);

                if (newIsOn != isOn) {
                    isOn = newIsOn;
                    updateState(CHANNEL_SWITCH, isOn ? OnOffType.ON : OnOffType.OFF);
                }

                if (!newMode.equals(mode)) {
                    mode = newMode;
                    updateState(CHANNEL_MODE, new StringType(mode));
                }

                int newBrightness = (currentBrightnessRaw * 100) / 255;
                if (newBrightness != brightness) {
                    brightness = newBrightness;
                    updateState(CHANNEL_BRIGHTNESS, new PercentType(brightness));
                }

                if (payload.length >= 5) {
                    int duration = payload[2] & 0xFF;
                    if (duration != sleepTime) {
                        sleepTime = duration;
                        updateState(CHANNEL_SLEEP, new DecimalType(sleepTime));
                    }
                }
            } else if (payload.length > 0) {
                // Fallback for very short status messages (just check mode)
                int modeRaw = payload[0] & 0xFF;
                String newMode;
                switch (modeRaw) {
                    case 0:
                        newMode = "OFF";
                        break;
                    case 1:
                        newMode = "DIMMER";
                        break;
                    case 2:
                        newMode = "BLINK";
                        break;
                    case 3:
                        newMode = "SWELL";
                        break;
                    default:
                        newMode = "OFF";
                        break;
                }

                boolean newIsOn = (modeRaw > 0);
                if (newIsOn != isOn) {
                    isOn = newIsOn;
                    updateState(CHANNEL_SWITCH, isOn ? OnOffType.ON : OnOffType.OFF);
                }

                if (!newMode.equals(mode)) {
                    mode = newMode;
                    updateState(CHANNEL_MODE, new StringType(mode));
                }
            }
        }
    }

    /**
     * Get the bridge handler.
     */
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
