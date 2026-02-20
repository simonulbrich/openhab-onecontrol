package org.openhab.binding.idsmyrv.internal.handler;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.idscan.SessionManager;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for IDS MyRV device handlers.
 * Provides common functionality for bridge handler access and session management.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public abstract class BaseIDSMyRVDeviceHandler extends BaseThingHandler implements IDSMyRVDeviceHandler {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * The bridge handler for sending commands. Set during initialization.
     * Package-private for testing.
     */
    @Nullable
    IDSMyRVBridgeHandler bridgeHandler;

    /**
     * Session manager for the device. Created as needed.
     */
    protected @Nullable SessionManager sessionManager;

    public BaseIDSMyRVDeviceHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        // Get bridge handler during initialization
        bridgeHandler = getBridgeHandler();
        
        // Call subclass initialization
        initializeDevice();
    }

    /**
     * Initialize device-specific configuration.
     * Called after bridge handler is set up.
     */
    protected abstract void initializeDevice();

    /**
     * Get the bridge handler for this device.
     * Returns the handler set during initialization, or null if not available.
     *
     * @return The bridge handler, or null if not available
     */
    @Nullable
    public IDSMyRVBridgeHandler getBridgeHandler() {
        // Return cached bridge handler if available
        if (bridgeHandler != null) {
            return bridgeHandler;
        }

        // Otherwise, try to get it from bridge
        Bridge bridge = getBridge();
        if (bridge == null) {
            return null;
        }

        ThingHandler handler = bridge.getHandler();
        if (handler instanceof IDSMyRVBridgeHandler bridgeHandlerInstance) {
            bridgeHandler = bridgeHandlerInstance;
            return bridgeHandler;
        }

        return null;
    }

    /**
     * Ensure a session is open with the target device.
     * This is a common method used by all device handlers.
     *
     * @param targetAddress The target device address
     * @param bridgeHandler The bridge handler to use for sending messages
     * @throws Exception If session cannot be opened
     */
    protected void ensureSession(Address targetAddress, IDSMyRVBridgeHandler bridgeHandler) throws Exception {
        SessionManager sm = sessionManager;

        // Check if we need a new session manager
        if (sm == null || !sm.getTargetAddress().equals(targetAddress)) {
            // Close the old session manager if it exists
            if (sm != null) {
                try {
                    sm.shutdown();
                    logger.debug("Closed old session manager for device {}", sm.getTargetAddress().getValue());
                } catch (Exception e) {
                    logger.debug("Error closing old session manager: {}", e.getMessage());
                }
            }

            // Create message sender callback
            SessionManager.MessageSender messageSender = (message) -> {
                bridgeHandler.sendMessage(message);
            };

            // Get scheduler from BaseThingHandler (protected field)
            ScheduledExecutorService scheduler = this.scheduler;
            if (scheduler == null) {
                throw new Exception("Scheduler not available");
            }

            sm = new SessionManager(bridgeHandler.getSourceAddress(), targetAddress, messageSender, scheduler);
            sessionManager = sm;
        }

        // If session is not open, open it
        if (!sm.isOpen()) {
            logger.info("🔐 Opening session with device {}", targetAddress.getValue());

            // Wait for gateway connection (max 5 seconds)
            int connectionAttempts = 0;
            while (!bridgeHandler.isConnected() && connectionAttempts < 50) {
                Thread.sleep(100);
                connectionAttempts++;
            }

            if (!bridgeHandler.isConnected()) {
                throw new Exception("Gateway not connected");
            }

            // Always close any existing session first (handles case where another client has it open)
            try {
                sm.closeSession();
                Thread.sleep(300); // Give device time to process close request
            } catch (Exception e) {
                logger.debug("Error closing existing session (may not exist): {}", e.getMessage());
            }

            // Request seed to start session
            sm.requestSeed();

            // Wait for session to open (with timeout)
            int attempts = 0;
            while (!sm.isOpen() && attempts < 30) {
                Thread.sleep(100);
                attempts++;
            }

            if (!sm.isOpen()) {
                throw new Exception("Session opening timed out");
            }

            logger.info("✅ Session opened ({}ms)", attempts * 100);
        } else {
            // Session already open, send heartbeat to reactivate
            sm.sendHeartbeat();

            // Small delay for heartbeat to be processed
            Thread.sleep(50);
        }

        // Update session activity
        sm.updateActivity();
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            // Re-get bridge handler when bridge comes online
            bridgeHandler = getBridgeHandler();
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Bridge is not online");
        }
    }

    @Override
    public void dispose() {
        // Close session
        SessionManager sm = sessionManager;
        if (sm != null && sm.isOpen()) {
            try {
                sm.closeSession();
            } catch (Exception e) {
                logger.debug("Failed to close session on dispose: {}", e.getMessage());
            }
        }

        bridgeHandler = null;
        sessionManager = null;

        super.dispose();
    }
}

