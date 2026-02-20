package org.openhab.binding.idsmyrv.internal.handler;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;
import org.openhab.binding.idsmyrv.internal.config.BridgeConfiguration;
import org.openhab.binding.idsmyrv.internal.discovery.IDSMyRVDeviceDiscoveryService;
import org.openhab.binding.idsmyrv.internal.gateway.CANConnection;
import org.openhab.binding.idsmyrv.internal.gateway.GatewayClient;
import org.openhab.binding.idsmyrv.internal.gateway.SocketCANClient;
import org.openhab.binding.idsmyrv.internal.idscan.IDSMessage;
import org.openhab.binding.idsmyrv.internal.idscan.MessageType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link IDSMyRVBridgeHandler} manages the connection to the CAN bus
 * (either via TCP gateway or direct SocketCAN adapter) and coordinates
 * communication with devices on the bus.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class IDSMyRVBridgeHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(IDSMyRVBridgeHandler.class);

    private @Nullable CANConnection canConnection;
    private @Nullable BridgeConfiguration config;
    private @Nullable ScheduledFuture<?> reconnectTask;
    private @Nullable IDSMyRVDeviceDiscoveryService deviceDiscoveryService;

    public IDSMyRVBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Bridge doesn't handle commands directly
    }

    @Override
    public void initialize() {
        logger.debug("Initializing IDS MyRV Bridge");

        config = getConfigAs(BridgeConfiguration.class);

        // Validate configuration
        BridgeConfiguration cfg = config;
        if (cfg == null || !cfg.isValid()) {
            if (cfg != null) {
                if (cfg.sourceAddress < 0 || cfg.sourceAddress >= 256) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "Invalid source address (must be 0-255)");
                } else if (cfg.port < 0 || cfg.port >= 65536) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "Invalid port (must be 0-65535)");
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid configuration");
                }
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Configuration not available");
            }
            return;
        }

        updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.NONE, "Connecting to gateway...");

        // Connect in background to avoid blocking the initialization
        scheduler.execute(this::connect);
    }

    /**
     * Connect to the CAN bus.
     * Uses either TCP gateway or SocketCAN based on configuration.
     */
    private void connect() {
        BridgeConfiguration cfg = config;
        if (cfg == null) {
            return;
        }

        try {
            if (cfg.isTcpMode()) {
                connectTcpGateway(cfg);
            } else if (cfg.isSocketCANMode()) {
                connectSocketCAN(cfg);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Invalid connection type: " + cfg.connectionType);
            }
        } catch (Exception e) {
            logger.warn("Failed to connect to CAN bus: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Connection failed: " + e.getMessage());

            // Schedule reconnect
            scheduleReconnect();
        }
    }

    /**
     * Connect to the CAN bus via TCP gateway.
     */
    private void connectTcpGateway(BridgeConfiguration cfg) throws Exception {
        String ipAddress = cfg.ipAddress;
        int port = cfg.port;

        // Check if IP address is configured
        boolean ipConfigured = ipAddress != null && !ipAddress.trim().isEmpty();

        // IP address should be set via OpenHAB discovery or manual configuration
        if (!ipConfigured) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "IP address not configured. Please add gateway via OpenHAB discovery or configure manually.");
            return;
        }

        logger.info("Connecting to CAN gateway at {}:{} with source address {}", ipAddress, port, cfg.sourceAddress);

        // Verify source address is valid
        if (cfg.sourceAddress < 0 || cfg.sourceAddress >= 256) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Invalid source address: " + cfg.sourceAddress + " (must be 0-255)");
            return;
        }

        GatewayClient client = new GatewayClient(ipAddress, port, this::handleCANMessage, scheduler, cfg.verbose,
                cfg.sourceAddress);

        client.connect();

        logger.debug("Gateway client created with source address {} (will be used for all CAN messages)",
                cfg.sourceAddress);

        this.canConnection = client;
        updateStatus(ThingStatus.ONLINE);

        logger.info("Successfully connected to CAN gateway");

        // Notify discovery service that bridge is online
        IDSMyRVDeviceDiscoveryService discovery = deviceDiscoveryService;
        if (discovery != null) {
            logger.debug("Bridge is online, discovery service should detect this via status monitoring");
        }

        // Cancel any pending reconnect task
        ScheduledFuture<?> task = reconnectTask;
        if (task != null) {
            task.cancel(false);
            reconnectTask = null;
        }
    }

    /**
     * Connect to the CAN bus via direct SocketCAN interface.
     */
    private void connectSocketCAN(BridgeConfiguration cfg) throws Exception {
        String canInterface = cfg.canInterface;

        if (canInterface == null || canInterface.trim().isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "CAN interface not configured. Please specify interface name (e.g., can0, vcan0).");
            return;
        }

        logger.info("Connecting to SocketCAN interface {} with source address {}", canInterface, cfg.sourceAddress);

        // Verify source address is valid
        if (cfg.sourceAddress < 0 || cfg.sourceAddress >= 256) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Invalid source address: " + cfg.sourceAddress + " (must be 0-255)");
            return;
        }

        SocketCANClient client = new SocketCANClient(canInterface, this::handleCANMessage, scheduler, cfg.verbose,
                cfg.sourceAddress);

        client.connect();

        logger.debug("SocketCAN client created with source address {} (will be used for all CAN messages)",
                cfg.sourceAddress);

        this.canConnection = client;
        updateStatus(ThingStatus.ONLINE);

        logger.info("Successfully connected to SocketCAN interface {}", canInterface);

        // Notify discovery service that bridge is online
        IDSMyRVDeviceDiscoveryService discovery = deviceDiscoveryService;
        if (discovery != null) {
            logger.debug("Bridge is online, discovery service should detect this via status monitoring");
        }

        // Cancel any pending reconnect task
        ScheduledFuture<?> task = reconnectTask;
        if (task != null) {
            task.cancel(false);
            reconnectTask = null;
        }
    }

    /**
     * Schedule a reconnection attempt.
     */
    private void scheduleReconnect() {
        ScheduledFuture<?> task = reconnectTask;
        if (task == null || task.isDone()) {
            reconnectTask = scheduler.schedule(this::connect, 30, TimeUnit.SECONDS);
            logger.debug("Scheduled reconnect in 30 seconds");
        }
    }

    /**
     * Set the device discovery service.
     * Called by OpenHAB when the discovery service is activated.
     *
     * @param discoveryService The discovery service
     */
    public void setDiscoveryService(IDSMyRVDeviceDiscoveryService discoveryService) {
        logger.info("Bridge handler received device discovery service (bridge status: {})", getThing().getStatus());
        this.deviceDiscoveryService = discoveryService;

        // If bridge is already online, notify the discovery service
        if (getThing().getStatus() == ThingStatus.ONLINE) {
            logger.info("Bridge is already online, notifying discovery service");
            // The discovery service will check bridge status in its monitoring loop
        }
        logger.debug("Device discovery service registered");
    }

    /**
     * Handle incoming CAN messages from the gateway.
     *
     * @param message The received CAN message
     */
    private void handleCANMessage(CANMessage message) {
        try {
            IDSMessage idsMessage = IDSMessage.decode(message);

            // Forward to discovery service if available and bridge is online
            IDSMyRVDeviceDiscoveryService discovery = deviceDiscoveryService;
            if (discovery != null && getThing().getStatus() == ThingStatus.ONLINE) {
                discovery.processMessage(message);
            }

            // Smart logging: only show messages that matter
            BridgeConfiguration cfg = config;
            MessageType msgType = idsMessage.getMessageType();

            if (cfg != null && cfg.verbose) {
                // Verbose mode: log everything except TEXT_CONSOLE spam
                if (msgType != MessageType.TEXT_CONSOLE) {
                    logger.debug("📨 {}", formatIDSMessage(idsMessage));
                }
            } else {
                // Normal mode: only log actionable messages at debug level
                switch (msgType) {
                    case REQUEST:
                    case RESPONSE:
                    case COMMAND:
                        logger.debug("📨 {}", formatIDSMessage(idsMessage));
                        break;
                    case DEVICE_STATUS:
                        // Only log status from devices we're managing
                        if (isDeviceManaged(idsMessage.getSourceAddress())) {
                            logger.debug("Status from device {}: {}", idsMessage.getSourceAddress().getValue(),
                                    formatHex(idsMessage.getData()));
                        }
                        break;
                    case NETWORK:
                    case CIRCUIT_ID:
                    case DEVICE_ID:
                    case PRODUCT_STATUS:
                    case TIME:
                    case EXT_STATUS:
                        logger.trace("Broadcast: {}", msgType.getName());
                        break;
                    case TEXT_CONSOLE:
                        // Complete silence for text console spam
                        break;
                    default:
                        logger.debug("Unknown message: {}", idsMessage);
                }
            }

            // Notify child handlers
            getThing().getThings().forEach(thing -> {
                if (thing.getHandler() instanceof IDSMyRVDeviceHandler deviceHandler) {
                    deviceHandler.handleIDSMessage(idsMessage);
                }
            });

        } catch (Exception e) {
            logger.debug("Failed to decode CAN message: {}", e.getMessage());
        }
    }

    /**
     * Check if a device address is managed by this bridge.
     */
    private boolean isDeviceManaged(Address address) {
        return getThing().getThings().stream().filter(thing -> thing.getHandler() instanceof IDSMyRVDeviceHandler)
                .map(thing -> (IDSMyRVDeviceHandler) thing.getHandler())
                .anyMatch(handler -> handler.getDeviceAddress().equals(address));
    }

    /**
     * Format an IDS message concisely.
     */
    private String formatIDSMessage(IDSMessage msg) {
        if (msg.getMessageType().isPointToPoint()) {
            return String.format("%s [%d→%d, data=0x%02X]: %s", msg.getMessageType().getName(),
                    msg.getSourceAddress().getValue(), msg.getTargetAddress().getValue(), msg.getMessageData(),
                    formatHex(msg.getData()));
        } else {
            return String.format("%s [%d]: %s", msg.getMessageType().getName(), msg.getSourceAddress().getValue(),
                    formatHex(msg.getData()));
        }
    }

    /**
     * Format byte array as hex string.
     */
    private String formatHex(byte[] data) {
        if (data.length == 0)
            return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < data.length; i++) {
            if (i > 0)
                sb.append(" ");
            sb.append(String.format("%02X", data[i] & 0xFF));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Get the bridge configuration.
     *
     * @return The bridge configuration, or null if not available
     */
    public @Nullable BridgeConfiguration getBridgeConfiguration() {
        return config;
    }

    /**
     * Send a CAN message to the bus.
     *
     * @param message The message to send
     * @throws Exception if sending fails
     */
    public void sendMessage(CANMessage message) throws Exception {
        CANConnection connection = canConnection;
        if (connection == null || !connection.isConnected()) {
            throw new Exception("CAN bus not connected");
        }

        connection.sendMessage(message);
    }

    /**
     * Get the source address configured for this bridge.
     *
     * @return The source address
     */
    /**
     * Get the source address configured for this bridge.
     * This is the address that will be used as the source in all CAN messages sent by this binding.
     *
     * @return The source address (defaults to 1 if config is not available)
     */
    public Address getSourceAddress() {
        BridgeConfiguration cfg = config;
        int addr = cfg != null ? cfg.sourceAddress : 1;
        if (cfg == null) {
            logger.warn("getSourceAddress() called but config is null, defaulting to 1");
        }
        return new Address(addr);
    }

    /**
     * Check if the CAN bus is connected.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        CANConnection connection = canConnection;
        return connection != null && connection.isConnected();
    }

    /**
     * Get the CAN connection instance.
     *
     * @return The CAN connection, or null if not initialized
     */
    public @Nullable CANConnection getCANConnection() {
        return canConnection;
    }

    @Override
    public void dispose() {
        logger.debug("Disposing IDS MyRV Bridge");

        // Cancel reconnect task
        ScheduledFuture<?> task = reconnectTask;
        if (task != null) {
            task.cancel(true);
            reconnectTask = null;
        }

        // Close CAN connection
        CANConnection connection = canConnection;
        if (connection != null) {
            connection.close();
            canConnection = null;
        }

        super.dispose();
    }
}
