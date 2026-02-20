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
import org.openhab.binding.idsmyrv.internal.idscan.IDSMessage;
import org.openhab.binding.idsmyrv.internal.idscan.MessageType;
import org.openhab.binding.idsmyrv.internal.idscan.SessionManager;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
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
 * The {@link RGBLightHandler} handles RGB light devices.
 * RGB lights support color control and various modes (On, Off, Blink, Jump, Fade, Rainbow).
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class RGBLightHandler extends BaseIDSMyRVDeviceHandler {

    private @Nullable DeviceConfiguration config;
    private @Nullable ScheduledFuture<?> commandTimeoutTask;

    private boolean isOn = false;
    private boolean waitingForStatus = false;

    // Current RGB state
    private String currentMode = "OFF";
    private int currentRed = 255;
    private int currentGreen = 255;
    private int currentBlue = 255;
    private int currentAutoOffDuration = 0;
    private int currentInterval = 200; // Default interval
    private int currentOnInterval = 200; // For blink mode
    private int currentOffInterval = 200; // For blink mode

    public RGBLightHandler(Thing thing) {
        super(thing);
    }

    @Override
    protected void initializeDevice() {
        logger.debug("Initializing RGB Light Handler for thing {}", getThing().getUID());

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

        String channelId = channelUID.getId();

        try {
            if (CHANNEL_SWITCH.equals(channelId)) {
                if (command instanceof OnOffType onOffCommand) {
                    handleSwitchCommand(onOffCommand);
                }
            } else if (CHANNEL_RGB_COLOR.equals(channelId)) {
                if (command instanceof HSBType hsbCommand) {
                    handleColorCommand(hsbCommand);
                }
            } else if (CHANNEL_RGB_MODE.equals(channelId)) {
                if (command instanceof StringType stringCommand) {
                    handleModeCommand(stringCommand.toString());
                }
            } else if (CHANNEL_RGB_SPEED.equals(channelId)) {
                if (command instanceof org.openhab.core.library.types.DecimalType decimalCommand) {
                    handleSpeedCommand(decimalCommand.intValue());
                }
            } else if (CHANNEL_RGB_SLEEP.equals(channelId)) {
                if (command instanceof org.openhab.core.library.types.DecimalType decimalCommand) {
                    handleSleepCommand(decimalCommand.intValue());
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
            // Use RESTORE command to turn on (restores last ON state)
            message = builder.setRGBRestore();
            isOn = true;
            currentMode = "ON";
            logger.info("💡 Sending ON (RESTORE) command to RGB light {}", cfg.address);
        } else {
            message = builder.setRGBOff(currentRed, currentGreen, currentBlue, currentAutoOffDuration, currentInterval);
            isOn = false;
            currentMode = "OFF";
            logger.info("💡 Sending OFF command to RGB light {}", cfg.address);
        }

        // Send command
        bridgeHandler.sendMessage(message);
        waitingForStatus = true;
        scheduleCommandTimeout();

        logger.debug("Sent {} command to RGB light {}", command, cfg.address);
    }

    /**
     * Handle a color command.
     * Converts HSB color to RGB and sends the appropriate command based on current mode.
     */
    private void handleColorCommand(HSBType color) throws Exception {
        DeviceConfiguration cfg = config;
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

        // Convert HSB to RGB
        // OpenHAB HSBType: Hue (0-360), Saturation (0-100), Brightness (0-100)
        // We need to convert to RGB (0-255 each)
        // Use standard HSB to RGB conversion algorithm
        float hue = color.getHue().floatValue();
        float saturation = color.getSaturation().floatValue() / 100.0f;
        float brightness = color.getBrightness().floatValue() / 100.0f;

        int red, green, blue;
        if (saturation == 0) {
            // Grayscale
            int gray = Math.round(brightness * 255);
            red = green = blue = gray;
        } else {
            float h = hue / 60.0f;
            int i = (int) Math.floor(h);
            float f = h - i;
            float p = brightness * (1 - saturation);
            float q = brightness * (1 - saturation * f);
            float t = brightness * (1 - saturation * (1 - f));

            switch (i % 6) {
                case 0:
                    red = Math.round(brightness * 255);
                    green = Math.round(t * 255);
                    blue = Math.round(p * 255);
                    break;
                case 1:
                    red = Math.round(q * 255);
                    green = Math.round(brightness * 255);
                    blue = Math.round(p * 255);
                    break;
                case 2:
                    red = Math.round(p * 255);
                    green = Math.round(brightness * 255);
                    blue = Math.round(t * 255);
                    break;
                case 3:
                    red = Math.round(p * 255);
                    green = Math.round(q * 255);
                    blue = Math.round(brightness * 255);
                    break;
                case 4:
                    red = Math.round(t * 255);
                    green = Math.round(p * 255);
                    blue = Math.round(brightness * 255);
                    break;
                default: // case 5
                    red = Math.round(brightness * 255);
                    green = Math.round(p * 255);
                    blue = Math.round(q * 255);
                    break;
            }
        }

        // Update current color
        currentRed = red;
        currentGreen = green;
        currentBlue = blue;

        // Build command based on current mode
        CommandBuilder builder = new CommandBuilder(bridgeHandler.getSourceAddress(), targetAddress);
        CANMessage message;

        if ("ON".equals(currentMode)) {
            message = builder.setRGBOn(red, green, blue, currentAutoOffDuration, currentInterval);
            logger.info("🎨 Sending color command to RGB light {}: RGB({},{},{}) in ON mode", cfg.address, red, green,
                    blue);
        } else if ("BLINK".equals(currentMode)) {
            message = builder.setRGBBlink(red, green, blue, currentAutoOffDuration, currentOnInterval,
                    currentOffInterval);
            logger.info("🎨 Sending color command to RGB light {}: RGB({},{},{}) in BLINK mode", cfg.address, red,
                    green, blue);
        } else {
            // If not in ON or BLINK mode, switch to ON mode with the new color
            currentMode = "ON";
            message = builder.setRGBOn(red, green, blue, currentAutoOffDuration, currentInterval);
            logger.info("🎨 Sending color command to RGB light {}: RGB({},{},{}) - switching to ON mode", cfg.address,
                    red, green, blue);
        }

        // Send command
        bridgeHandler.sendMessage(message);
        waitingForStatus = true;
        scheduleCommandTimeout();

        logger.debug("Sent color command to RGB light {}: RGB({},{},{})", cfg.address, red, green, blue);
    }

    /**
     * Handle a mode command.
     * Supports: OFF, ON, BLINK, JUMP3, JUMP7, FADE3, FADE7, RAINBOW
     */
    private void handleModeCommand(String mode) throws Exception {
        DeviceConfiguration cfg = config;
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

        // Build command based on mode
        CommandBuilder builder = new CommandBuilder(bridgeHandler.getSourceAddress(), targetAddress);
        CANMessage message;
        String modeUpper = mode.toUpperCase();

        switch (modeUpper) {
            case "OFF":
                message = builder.setRGBOff(currentRed, currentGreen, currentBlue, currentAutoOffDuration,
                        currentInterval);
                isOn = false;
                currentMode = "OFF";
                logger.info("💡 Sending OFF command to RGB light {}", cfg.address);
                break;

            case "ON":
                message = builder.setRGBOn(currentRed, currentGreen, currentBlue, currentAutoOffDuration,
                        currentInterval);
                isOn = true;
                currentMode = "ON";
                logger.info("💡 Sending ON command to RGB light {}: RGB({},{},{})", cfg.address, currentRed,
                        currentGreen, currentBlue);
                break;

            case "BLINK":
                message = builder.setRGBBlink(currentRed, currentGreen, currentBlue, currentAutoOffDuration,
                        currentOnInterval, currentOffInterval);
                isOn = true;
                currentMode = "BLINK";
                logger.info("💡 Sending BLINK command to RGB light {}: RGB({},{},{})", cfg.address, currentRed,
                        currentGreen, currentBlue);
                break;

            case "JUMP3":
                message = builder.setRGBTransition(CommandBuilder.RGB_MODE_JUMP3, currentAutoOffDuration,
                        currentInterval);
                isOn = true;
                currentMode = "JUMP3";
                logger.info("💡 Sending JUMP3 command to RGB light {}", cfg.address);
                break;

            case "JUMP7":
                message = builder.setRGBTransition(CommandBuilder.RGB_MODE_JUMP7, currentAutoOffDuration,
                        currentInterval);
                isOn = true;
                currentMode = "JUMP7";
                logger.info("💡 Sending JUMP7 command to RGB light {}", cfg.address);
                break;

            case "FADE3":
                message = builder.setRGBTransition(CommandBuilder.RGB_MODE_FADE3, currentAutoOffDuration,
                        currentInterval);
                isOn = true;
                currentMode = "FADE3";
                logger.info("💡 Sending FADE3 command to RGB light {}", cfg.address);
                break;

            case "FADE7":
                message = builder.setRGBTransition(CommandBuilder.RGB_MODE_FADE7, currentAutoOffDuration,
                        currentInterval);
                isOn = true;
                currentMode = "FADE7";
                logger.info("💡 Sending FADE7 command to RGB light {}", cfg.address);
                break;

            case "RAINBOW":
                message = builder.setRGBTransition(CommandBuilder.RGB_MODE_RAINBOW, currentAutoOffDuration,
                        currentInterval);
                isOn = true;
                currentMode = "RAINBOW";
                logger.info("💡 Sending RAINBOW command to RGB light {}", cfg.address);
                break;

            default:
                logger.warn("Invalid RGB mode: {}", mode);
                return;
        }

        // Send command
        bridgeHandler.sendMessage(message);
        waitingForStatus = true;
        scheduleCommandTimeout();

        logger.debug("Sent mode command to RGB light {}: {}", cfg.address, modeUpper);
    }

    /**
     * Handle a speed/rate command.
     * Updates the interval for blink and transition modes.
     */
    private void handleSpeedCommand(int speed) throws Exception {
        DeviceConfiguration cfg = config;
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

        // Normalize speed to 0-65535
        speed = Math.max(0, Math.min(65535, speed));

        // Build command based on current mode
        CommandBuilder builder = new CommandBuilder(bridgeHandler.getSourceAddress(), targetAddress);
        CANMessage message;

        switch (currentMode) {
            case "BLINK":
                // For BLINK mode, speed is split into onInterval and offInterval
                // Use the same value for both (as per C# code)
                currentOnInterval = (byte) (speed >> 8);
                currentOffInterval = (byte) (speed & 0xFF);
                message = builder.setRGBBlink(currentRed, currentGreen, currentBlue, currentAutoOffDuration,
                        currentOnInterval, currentOffInterval);
                logger.info("⚡ Sending speed command to RGB light {}: {}ms (BLINK mode)", cfg.address, speed);
                break;

            case "JUMP3":
            case "JUMP7":
            case "FADE3":
            case "FADE7":
            case "RAINBOW":
                // For transition modes, speed is the interval
                currentInterval = speed;
                int modeValue;
                switch (currentMode) {
                    case "JUMP3":
                        modeValue = CommandBuilder.RGB_MODE_JUMP3;
                        break;
                    case "JUMP7":
                        modeValue = CommandBuilder.RGB_MODE_JUMP7;
                        break;
                    case "FADE3":
                        modeValue = CommandBuilder.RGB_MODE_FADE3;
                        break;
                    case "FADE7":
                        modeValue = CommandBuilder.RGB_MODE_FADE7;
                        break;
                    case "RAINBOW":
                        modeValue = CommandBuilder.RGB_MODE_RAINBOW;
                        break;
                    default:
                        logger.warn("Invalid mode for speed command: {}", currentMode);
                        return;
                }
                message = builder.setRGBTransition(modeValue, currentAutoOffDuration, speed);
                logger.info("⚡ Sending speed command to RGB light {}: {}ms ({} mode)", cfg.address, speed, currentMode);
                break;

            default:
                logger.debug("Speed command ignored - not in a mode that supports speed control");
                return;
        }

        // Send command
        bridgeHandler.sendMessage(message);
        waitingForStatus = true;
        scheduleCommandTimeout();

        logger.debug("Sent speed command to RGB light {}: {}ms", cfg.address, speed);
    }

    /**
     * Handle a sleep/auto-off duration command.
     * Updates the auto-off duration for the current mode.
     */
    private void handleSleepCommand(int sleepSeconds) throws Exception {
        DeviceConfiguration cfg = config;
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

        // Normalize sleep to 0-255
        sleepSeconds = Math.max(0, Math.min(255, sleepSeconds));
        currentAutoOffDuration = sleepSeconds;

        // Build command based on current mode
        CommandBuilder builder = new CommandBuilder(bridgeHandler.getSourceAddress(), targetAddress);
        CANMessage message;

        switch (currentMode) {
            case "ON":
                message = builder.setRGBOn(currentRed, currentGreen, currentBlue, sleepSeconds, currentInterval);
                logger.info("😴 Sending sleep command to RGB light {}: {}s (ON mode)", cfg.address, sleepSeconds);
                break;

            case "BLINK":
                message = builder.setRGBBlink(currentRed, currentGreen, currentBlue, sleepSeconds, currentOnInterval,
                        currentOffInterval);
                logger.info("😴 Sending sleep command to RGB light {}: {}s (BLINK mode)", cfg.address, sleepSeconds);
                break;

            case "JUMP3":
            case "JUMP7":
            case "FADE3":
            case "FADE7":
            case "RAINBOW":
                int modeValue;
                switch (currentMode) {
                    case "JUMP3":
                        modeValue = CommandBuilder.RGB_MODE_JUMP3;
                        break;
                    case "JUMP7":
                        modeValue = CommandBuilder.RGB_MODE_JUMP7;
                        break;
                    case "FADE3":
                        modeValue = CommandBuilder.RGB_MODE_FADE3;
                        break;
                    case "FADE7":
                        modeValue = CommandBuilder.RGB_MODE_FADE7;
                        break;
                    case "RAINBOW":
                        modeValue = CommandBuilder.RGB_MODE_RAINBOW;
                        break;
                    default:
                        logger.warn("Invalid mode for sleep command: {}", currentMode);
                        return;
                }
                message = builder.setRGBTransition(modeValue, sleepSeconds, currentInterval);
                logger.info("😴 Sending sleep command to RGB light {}: {}s ({} mode)", cfg.address, sleepSeconds,
                        currentMode);
                break;

            default:
                logger.debug("Sleep command ignored - not in a mode that supports sleep control");
                return;
        }

        // Send command
        bridgeHandler.sendMessage(message);
        waitingForStatus = true;
        scheduleCommandTimeout();

        logger.debug("Sent sleep command to RGB light {}: {}s", cfg.address, sleepSeconds);
    }

    /**
     * Ensure a session is open with the target device.
     */
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
                IDSMyRVBridgeHandler bridgeHandler = getBridgeHandler();
                if (bridgeHandler != null && message.getTargetAddress().equals(bridgeHandler.getSourceAddress())) {
                    logger.debug("🔄 Forwarding RESPONSE to SessionManager: from device {}",
                            message.getSourceAddress().getValue());
                    sm.processResponse(message);
                }
            }
        }

        // Only process status messages from our device
        if (message.getMessageType() == MessageType.DEVICE_STATUS
                && message.getSourceAddress().getValue() == cfg.address) {
            updateRGBStatus(message.getData());
        }
    }

    /**
     * Update RGB light status from device status message.
     * Based on C# LogicalDeviceLightRgbStatus format:
     * - Byte 0: Mode (0=Off, 1=On, 2=Blink, 4=Jump3, 5=Jump7, 6=Fade3, 7=Fade7, 8=Rainbow)
     * - Bytes 1-3: RGB color (R, G, B, 0-255 each)
     * - Byte 4: AutoOffDuration (0-255 seconds)
     * - Bytes 5-6: Interval (uint16, big-endian) or OnInterval/OffInterval (for Blink)
     * - Byte 7: Unused
     */
    private void updateRGBStatus(byte[] data) {
        if (data.length < 1) {
            logger.debug("RGB status message too short");
            return;
        }

        // Parse mode
        int modeRaw = data[0] & 0xFF;
        String newMode;
        switch (modeRaw) {
            case 0:
                newMode = "OFF";
                break;
            case 1:
                newMode = "ON";
                break;
            case 2:
                newMode = "BLINK";
                break;
            case 4:
                newMode = "JUMP3";
                break;
            case 5:
                newMode = "JUMP7";
                break;
            case 6:
                newMode = "FADE3";
                break;
            case 7:
                newMode = "FADE7";
                break;
            case 8:
                newMode = "RAINBOW";
                break;
            default:
                newMode = "OFF";
                break;
        }

        // Mode > 0 means light is on
        boolean newIsOn = (modeRaw > 0);

        // Parse color if available
        // Only update color channel for ON and OFF modes to avoid overwhelming logs
        // Other modes (BLINK, JUMP3, JUMP7, FADE3, FADE7, RAINBOW) constantly change color
        boolean shouldUpdateColorChannel = "ON".equals(newMode) || "OFF".equals(newMode);

        if (data.length >= 4) {
            int newRed = data[1] & 0xFF;
            int newGreen = data[2] & 0xFF;
            int newBlue = data[3] & 0xFF;

            if (newRed != currentRed || newGreen != currentGreen || newBlue != currentBlue) {
                currentRed = newRed;
                currentGreen = newGreen;
                currentBlue = newBlue;

                // Only update color channel state and log for ON/OFF modes
                if (shouldUpdateColorChannel) {
                    // Convert RGB to HSB for OpenHAB
                    // OpenHAB expects HSBType with hue (0-360), saturation (0-100), brightness (0-100)
                    HSBType hsbColor = HSBType.fromRGB(newRed, newGreen, newBlue);
                    updateState(CHANNEL_RGB_COLOR, hsbColor);
                    logger.debug("Updated RGB light color: RGB({},{},{}) -> HSB({})", newRed, newGreen, newBlue,
                            hsbColor);
                }
                // For other modes, skip channel update entirely to avoid log spam
                // Internal state variables are still updated above for command building
            }
        }

        // Parse auto-off duration if available
        if (data.length >= 5) {
            int newAutoOffDuration = data[4] & 0xFF;
            if (newAutoOffDuration != currentAutoOffDuration) {
                currentAutoOffDuration = newAutoOffDuration;
                updateState(CHANNEL_RGB_SLEEP, new org.openhab.core.library.types.DecimalType(currentAutoOffDuration));
                logger.debug("Updated RGB light sleep time: {}s", currentAutoOffDuration);
            }
        }

        // Parse interval/onInterval/offInterval if available
        if (data.length >= 7) {
            if ("BLINK".equals(newMode)) {
                // For BLINK mode, bytes 5-6 are separate onInterval and offInterval
                int newOnInterval = data[5] & 0xFF;
                int newOffInterval = data[6] & 0xFF;
                if (newOnInterval != currentOnInterval || newOffInterval != currentOffInterval) {
                    currentOnInterval = newOnInterval;
                    currentOffInterval = newOffInterval;
                    // For BLINK mode, speed is (onInterval << 8) | offInterval
                    int speed = (currentOnInterval << 8) | currentOffInterval;
                    updateState(CHANNEL_RGB_SPEED, new org.openhab.core.library.types.DecimalType(speed));
                    logger.debug("Updated RGB light speed: {}ms (BLINK: on={}, off={})", speed, currentOnInterval,
                            currentOffInterval);
                }
            } else {
                // For other modes, bytes 5-6 are a uint16 interval
                int newInterval = ((data[5] & 0xFF) << 8) | (data[6] & 0xFF);
                if (newInterval != currentInterval) {
                    currentInterval = newInterval;
                    updateState(CHANNEL_RGB_SPEED, new org.openhab.core.library.types.DecimalType(currentInterval));
                    logger.debug("Updated RGB light speed: {}ms", currentInterval);
                }
            }
        }

        // Update switch state
        if (newIsOn != isOn) {
            isOn = newIsOn;
            updateState(CHANNEL_SWITCH, isOn ? OnOffType.ON : OnOffType.OFF);
            logger.debug("Updated RGB light switch state: {}", isOn ? "ON" : "OFF");
        }

        // Update mode
        if (!newMode.equals(currentMode)) {
            currentMode = newMode;
            updateState(CHANNEL_RGB_MODE, new StringType(currentMode));
            logger.debug("Updated RGB light mode: {}", currentMode);
        }

        if (waitingForStatus) {
            waitingForStatus = false;
            ScheduledFuture<?> task = commandTimeoutTask;
            if (task != null) {
                task.cancel(false);
                commandTimeoutTask = null;
            }
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
