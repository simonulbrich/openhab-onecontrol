package org.openhab.binding.idsmyrv.internal.handler;

import static org.openhab.binding.idsmyrv.internal.IDSMyRVBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.config.DeviceConfiguration;
import org.openhab.binding.idsmyrv.internal.idscan.IDSMessage;
import org.openhab.binding.idsmyrv.internal.idscan.MessageType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;

/**
 * The {@link TankSensorHandler} handles tank sensor devices.
 * Tank sensors are read-only devices that report tank level.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class TankSensorHandler extends BaseIDSMyRVDeviceHandler {

    private @Nullable DeviceConfiguration config;

    public TankSensorHandler(Thing thing) {
        super(thing);
    }

    @Override
    protected void initializeDevice() {
        logger.debug("Initializing Tank Sensor Handler for thing {}", getThing().getUID());

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

        // Tank sensors are read-only
        logger.debug("Tank sensor is read-only, ignoring command {}", command);
    }

    @Override
    public void handleIDSMessage(IDSMessage message) {
        DeviceConfiguration cfg = config;
        if (cfg == null) {
            return;
        }

        // Only process messages from our device
        if (message.getSourceAddress().getValue() != cfg.address) {
            return;
        }

        if (message.getMessageType() == MessageType.DEVICE_STATUS) {
            updateTankLevel(message.getData());
        }
    }

    /**
     * Update tank level from device status message.
     * Tank level is typically in byte 0 or bytes 0-1 of the status message.
     */
    private void updateTankLevel(byte[] data) {
        if (data.length < 1) {
            logger.debug("Tank status message too short");
            return;
        }

        // Tank level is typically a percentage (0-100) in byte 0
        int level = data[0] & 0xFF;
        if (level > 100) {
            level = 100; // Clamp to 100%
        }

        updateState(CHANNEL_TANK_LEVEL, new DecimalType(level));
        logger.debug("Updated tank level: {}%", level);
    }

    // bridgeStatusChanged() is now inherited from BaseIDSMyRVDeviceHandler
}
