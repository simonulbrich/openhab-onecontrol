package org.openhab.binding.idsmyrv.internal.discovery;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.openhab.binding.idsmyrv.internal.idscan.CommandBuilder;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.idsmyrv.internal.IDSMyRVBindingConstants;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;
import org.openhab.binding.idsmyrv.internal.handler.IDSMyRVBridgeHandler;
import org.openhab.binding.idsmyrv.internal.idscan.DeviceType;
import org.openhab.binding.idsmyrv.internal.idscan.FunctionName;
import org.openhab.binding.idsmyrv.internal.idscan.IDSMessage;
import org.openhab.binding.idsmyrv.internal.idscan.MessageType;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovery service for IDS MyRV devices on the CAN bus.
 *
 * Discovers devices by listening to CAN messages. When a DEVICE_ID message
 * is received, it identifies the device type and creates a discovery result
 * if the device type is supported.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
@Component(service = { org.openhab.core.config.discovery.DiscoveryService.class, ThingHandlerService.class })
public class IDSMyRVDeviceDiscoveryService extends AbstractDiscoveryService implements ThingHandlerService {

    private static final Logger logger = LoggerFactory.getLogger(IDSMyRVDeviceDiscoveryService.class);

    private static final int DISCOVERY_TIMEOUT_SECONDS = 30;

    // Track discovered devices to avoid duplicate discovery results
    private final Map<Address, DiscoveredDeviceInfo> discoveredDevices = new ConcurrentHashMap<>();

    private @Nullable IDSMyRVBridgeHandler bridgeHandler;
    private @Nullable ScheduledFuture<?> cleanupTask;
    private @Nullable ScheduledExecutorService scheduler;
    private @Nullable ScheduledFuture<?> initialDiscoveryDelayTask; // Task for delayed initial discovery
    private @Nullable ScheduledFuture<?> bridgeStatusMonitorTask; // Task for periodic bridge status monitoring
    private boolean bridgeWasOnline = false; // Track if bridge was online to detect transitions
    private boolean initialDiscoveryDone = false; // Track if we've done the initial discovery pass
    private boolean manualScanActive = false; // Track if a manual scan is active
    private @Nullable ThingRegistry thingRegistry;

    /**
     * Information about a discovered device.
     */
    private static class DiscoveredDeviceInfo {
        final Address address;
        DeviceType deviceType; // May be UNKNOWN until we get DEVICE_ID
        FunctionName functionName; // Function name from DEVICE_ID message
        int deviceInstance; // Device instance from DEVICE_ID message
        int deviceCapabilities; // Device capabilities byte (from byte 7 of DEVICE_ID), -1 if not present
        final long firstSeen;
        long lastSeen;
        boolean published;
        boolean hasDeviceID; // True if we've received a DEVICE_ID message
        long lastIDRequest; // When we last requested device ID
        long lastRegistryCheck; // When we last checked the registry for this device

        DiscoveredDeviceInfo(Address address) {
            this.address = address;
            this.deviceType = DeviceType.UNKNOWN;
            this.functionName = FunctionName.UNKNOWN;
            this.deviceInstance = 0;
            this.deviceCapabilities = -1; // -1 means not available
            this.firstSeen = System.currentTimeMillis();
            this.lastSeen = this.firstSeen;
            this.published = false;
            this.hasDeviceID = false;
            this.lastIDRequest = 0;
            this.lastRegistryCheck = 0;
        }

        void updateLastSeen() {
            this.lastSeen = System.currentTimeMillis();
        }

        void setDeviceID(DeviceType deviceType, FunctionName functionName, int deviceInstance, int deviceCapabilities) {
            this.deviceType = deviceType;
            this.functionName = functionName;
            this.deviceInstance = deviceInstance;
            this.deviceCapabilities = deviceCapabilities;
            this.hasDeviceID = true;
        }

        /**
         * Check if position is supported based on capability flags.
         * Position is supported if SupportsCoarsePosition (bit 1 = 0x02) or
         * SupportsFinePosition (bit 2 = 0x04) flags are set.
         */
        boolean isPositionSupported() {
            if (deviceCapabilities < 0) {
                return false; // Capabilities not available
            }
            // Check for SupportsCoarsePosition (0x02) or SupportsFinePosition (0x04)
            return (deviceCapabilities & 0x02) != 0 || (deviceCapabilities & 0x04) != 0;
        }
    }

    public IDSMyRVDeviceDiscoveryService() {
        super(Collections.unmodifiableSet(createSupportedThingTypes()), DISCOVERY_TIMEOUT_SECONDS, true);
    }

    private static Set<ThingTypeUID> createSupportedThingTypes() {
        Set<ThingTypeUID> types = new HashSet<>();
        types.add(IDSMyRVBindingConstants.THING_TYPE_LIGHT);
        types.add(IDSMyRVBindingConstants.THING_TYPE_HVAC);
        return types;
    }

    @Override
    public void setThingHandler(ThingHandler handler) {
        logger.info("setThingHandler called with handler: {} (type: {})",
                handler != null ? handler.getClass().getSimpleName() : "null",
                handler != null ? handler.getClass().getName() : "null");

        if (handler instanceof IDSMyRVBridgeHandler) {
            IDSMyRVBridgeHandler oldHandler = this.bridgeHandler;
            this.bridgeHandler = (IDSMyRVBridgeHandler) handler;
            // Register ourselves to receive CAN messages
            if (bridgeHandler != null) {
                bridgeHandler.setDiscoveryService(this);
                ThingStatus bridgeStatus = bridgeHandler.getThing().getStatus();
                logger.info("✅ Discovery service bound to bridge handler (bridge status: {}, thingUID: {})",
                        bridgeStatus, bridgeHandler.getThing().getUID());

                // Start background discovery if enabled
                if (isBackgroundDiscoveryEnabled() && oldHandler == null) {
                    logger.info("Starting background discovery");
                    startBackgroundDiscovery();
                }

                // Check if bridge is already online
                if (bridgeStatus == ThingStatus.ONLINE) {
                    bridgeWasOnline = true;
                    logger.info("Bridge is already online, starting initial device discovery");
                    startInitialDiscovery();
                } else {
                    bridgeWasOnline = false;
                    logger.info(
                            "Bridge is not yet online (status: {}), will start discovery when bridge comes online via bridgeStatusChanged()",
                            bridgeStatus);
                }
            }
        } else {
            logger.warn("Discovery service received non-bridge handler: {} (expected IDSMyRVBridgeHandler)",
                    handler != null ? handler.getClass().getName() : "null");
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return bridgeHandler;
    }

    /**
     * Check bridge status and start discovery if bridge just came online.
     * This is called from processMessage() when messages arrive, and from status monitoring.
     */
    private void checkBridgeStatusAndStartDiscovery() {
        IDSMyRVBridgeHandler bridge = bridgeHandler;
        if (bridge == null) {
            return;
        }

        ThingStatus currentStatus = bridge.getThing().getStatus();

        if (currentStatus == ThingStatus.ONLINE) {
            if (!bridgeWasOnline) {
                // Bridge just came online - start discovery
                bridgeWasOnline = true;
                initialDiscoveryDone = false;
                logger.info("✅ Bridge came online (detected via status check), starting initial device discovery");
                startInitialDiscovery();
            }
        } else {
            // Bridge is offline
            if (bridgeWasOnline) {
                bridgeWasOnline = false;
                initialDiscoveryDone = false;
                logger.info("⚠️ Bridge went offline (status: {}), will restart discovery when it comes back online",
                        currentStatus);
                // Cancel any pending initial discovery
                if (initialDiscoveryDelayTask != null) {
                    initialDiscoveryDelayTask.cancel(false);
                    initialDiscoveryDelayTask = null;
                }
            }
        }
    }

    @Override
    public void activate() {
        super.activate(null);
        logger.info("Device discovery service activated (OSGi component) - background discovery enabled: {}",
                isBackgroundDiscoveryEnabled());

        // Create our own scheduler if needed
        if (scheduler == null) {
            scheduler = Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "IDSMyRV-DeviceDiscovery");
                t.setDaemon(true);
                return t;
            });
            logger.debug("Created own ScheduledExecutorService for device discovery");
        }

        // Check for existing bridge things and bind to them
        // OpenHAB only calls setThingHandler when a thing is created, not when service activates
        checkForExistingBridgeThings();

        // Start periodic bridge status monitoring to detect when bridge comes online
        startBridgeStatusMonitoring();
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void setThingRegistry(ThingRegistry thingRegistry) {
        this.thingRegistry = thingRegistry;
        logger.debug("ThingRegistry injected");
        // Check for existing bridge things when registry becomes available
        if (bridgeHandler == null) {
            checkForExistingBridgeThings();
        }
    }

    protected void unsetThingRegistry(ThingRegistry thingRegistry) {
        if (this.thingRegistry == thingRegistry) {
            this.thingRegistry = null;
        }
    }

    /**
     * Check for existing bridge things and manually bind to them.
     * This is needed because setThingHandler is only called when a thing is created,
     * not when the service activates after the thing already exists.
     */
    private void checkForExistingBridgeThings() {
        ThingRegistry registry = thingRegistry;
        if (registry == null) {
            logger.info("🔍 ThingRegistry not available yet, will check when it becomes available");
            return;
        }

        if (bridgeHandler != null) {
            logger.debug("Already bound to a bridge handler, skipping check for existing things");
            return;
        }

        if (scheduler == null) {
            logger.info("🔍 Scheduler not available yet, cannot check for existing things");
            return;
        }

        // Find all bridge things of our type
        ThingTypeUID bridgeTypeUID = IDSMyRVBindingConstants.THING_TYPE_BRIDGE;
        logger.info("🔍 Checking for existing bridge things of type {} in registry (total things: {})", bridgeTypeUID,
                registry.getAll().size());

        boolean foundThing = false;
        int bridgeThingCount = 0;
        for (Thing thing : registry.getAll()) {
            if (thing.getThingTypeUID().equals(bridgeTypeUID)) {
                bridgeThingCount++;
                foundThing = true;
                ThingUID thingUID = thing.getUID();
                ThingStatus thingStatus = thing.getStatus();
                ThingHandler handler = thing.getHandler();

                logger.info("🔍 Found bridge thing: UID={}, Status={}, Handler={}", thingUID, thingStatus,
                        handler != null ? handler.getClass().getSimpleName() : "null");

                if (handler instanceof IDSMyRVBridgeHandler) {
                    logger.info("✅ Found existing bridge thing {} with handler, manually binding", thingUID);
                    // Manually call setThingHandler to bind to this existing bridge
                    setThingHandler(handler);
                    return; // Only bind to the first bridge we find
                } else {
                    logger.info("⏳ Bridge thing {} exists but handler is not yet available (status: {}, handler: {})",
                            thingUID, thingStatus, handler != null ? handler.getClass().getName() : "null");
                }
            }
        }

        logger.info("🔍 Check complete: found {} bridge thing(s) of type {}", bridgeThingCount, bridgeTypeUID);

        if (foundThing) {
            // Handler not available yet, schedule retries with exponential backoff
            logger.info("⏳ Bridge thing found but handler not yet available, will retry in 2 seconds (max 10 retries)");
            scheduleRetryCheck(1, 10);
        } else {
            logger.info("ℹ️ No existing bridge things found in registry");
        }
    }

    private @Nullable ScheduledFuture<?> retryCheckTask;

    /**
     * Schedule a retry to check for bridge handler with exponential backoff.
     *
     * @param attempt Current attempt number (1-based)
     * @param maxAttempts Maximum number of attempts
     */
    private void scheduleRetryCheck(int attempt, int maxAttempts) {
        if (bridgeHandler != null || scheduler == null || attempt > maxAttempts) {
            if (attempt > maxAttempts) {
                logger.warn("⚠️ Maximum retry attempts ({}) reached, giving up on finding bridge handler", maxAttempts);
            }
            return;
        }

        // Cancel any existing retry task
        ScheduledFuture<?> existing = retryCheckTask;
        if (existing != null) {
            existing.cancel(false);
        }

        long delaySeconds = Math.min(2L * attempt, 10L); // Exponential backoff, max 10 seconds
        logger.debug("Scheduling retry #{} in {} seconds", attempt, delaySeconds);

        retryCheckTask = scheduler.schedule(() -> {
            if (bridgeHandler == null) {
                logger.info("🔄 Retry #{}: Checking for existing bridge things...", attempt);
                // Re-check for bridge things, but this time don't schedule another retry
                // Instead, let the checkForExistingBridgeThings method handle it
                ThingRegistry registry = thingRegistry;
                if (registry != null && scheduler != null) {
                    ThingTypeUID bridgeTypeUID = IDSMyRVBindingConstants.THING_TYPE_BRIDGE;
                    for (Thing thing : registry.getAll()) {
                        if (thing.getThingTypeUID().equals(bridgeTypeUID)) {
                            ThingHandler handler = thing.getHandler();
                            if (handler instanceof IDSMyRVBridgeHandler) {
                                logger.info("✅ Found bridge handler on retry #{}: {}", attempt, thing.getUID());
                                setThingHandler(handler);
                                retryCheckTask = null;
                                return;
                            } else {
                                // Schedule next retry
                                scheduleRetryCheck(attempt + 1, maxAttempts);
                                return;
                            }
                        }
                    }
                    // No bridge thing found, schedule next retry
                    scheduleRetryCheck(attempt + 1, maxAttempts);
                }
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * Start periodic monitoring of bridge status to detect when it comes online.
     * This is a backup mechanism in case we miss the initial binding.
     */
    private void startBridgeStatusMonitoring() {
        if (scheduler == null) {
            logger.debug("Cannot start bridge status monitoring: scheduler not available");
            return;
        }

        // Cancel existing task if any
        ScheduledFuture<?> existing = bridgeStatusMonitorTask;
        if (existing != null) {
            existing.cancel(false);
        }

        // Check bridge status every 2 seconds
        bridgeStatusMonitorTask = scheduler.scheduleWithFixedDelay(() -> {
            if (bridgeHandler != null) {
                checkBridgeStatusAndStartDiscovery();
            }
        }, 2, 2, TimeUnit.SECONDS);

        logger.debug("Started bridge status monitoring (checking every 2 seconds)");
    }

    @Override
    public void deactivate() {
        logger.info("Device discovery service deactivating");
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
            cleanupTask = null;
        }
        if (initialDiscoveryDelayTask != null) {
            initialDiscoveryDelayTask.cancel(false);
            initialDiscoveryDelayTask = null;
        }
        if (retryCheckTask != null) {
            retryCheckTask.cancel(false);
            retryCheckTask = null;
        }
        if (bridgeStatusMonitorTask != null) {
            bridgeStatusMonitorTask.cancel(false);
            bridgeStatusMonitorTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
        discoveredDevices.clear();
        bridgeWasOnline = false;
        initialDiscoveryDone = false;
        super.deactivate();
        logger.info("Device discovery service deactivated");
    }

    @Override
    protected void startScan() {
        logger.info("Starting manual device discovery scan");
        manualScanActive = true;

        IDSMyRVBridgeHandler bridge = bridgeHandler;
        if (bridge == null) {
            logger.warn("Cannot start manual scan: bridge handler not set");
            manualScanActive = false;
            return;
        }

        // Check if bridge is online
        ThingStatus bridgeStatus = bridge.getThing().getStatus();
        if (bridgeStatus != ThingStatus.ONLINE) {
            logger.warn("Cannot start manual scan: bridge is not online (status: {})", bridgeStatus);
            manualScanActive = false;
            return;
        }

        // Actively scan for devices by querying all possible addresses (0-254)
        logger.info("Scanning addresses 0-254 for devices...");
        scanForDevices();

        // Start periodic device ID requests for unknown devices
        startDeviceIDRequests();

        // Wait a bit for responses, then request IDs from any devices that responded
        if (scheduler != null) {
            scheduler.schedule(() -> {
                IDSMyRVBridgeHandler currentBridge = bridgeHandler;
                if (currentBridge == null) {
                    logger.debug("Bridge handler lost during manual scan");
                    return;
                }

                ThingStatus currentStatus = currentBridge.getThing().getStatus();
                if (currentStatus != ThingStatus.ONLINE) {
                    logger.warn("Bridge went offline during manual scan (status: {})", currentStatus);
                    return;
                }

                logger.info("Manual scan complete, found {} devices. Requesting device IDs for unidentified devices...",
                        discoveredDevices.size());

                // Request device IDs from all devices we've seen but haven't identified yet
                requestDeviceIDsForAllDevices();
            }, 3, TimeUnit.SECONDS);
        }
    }

    @Override
    protected synchronized void stopScan() {
        logger.debug("Stopping device discovery scan");
        manualScanActive = false;
        super.stopScan();
    }

    /**
     * Process a CAN message from the bridge handler.
     * Called by the bridge handler when it receives a message.
     */
    public void processMessage(CANMessage canMessage) {
        // Check if discovery is enabled (background or manual scan)
        if (!isBackgroundDiscoveryEnabled() && !manualScanActive) {
            logger.trace("Discovery is disabled and no manual scan active, ignoring message");
            return;
        }

        // Check if bridge is online
        IDSMyRVBridgeHandler bridge = bridgeHandler;
        if (bridge == null) {
            logger.trace("Bridge handler not set, ignoring message");
            return;
        }

        ThingStatus bridgeStatus = bridge.getThing().getStatus();

        // Check if bridge just came online and start discovery if needed
        checkBridgeStatusAndStartDiscovery();

        if (bridgeStatus != ThingStatus.ONLINE) {
            logger.trace("Bridge is not online (status: {}), ignoring message", bridgeStatus);
            return;
        }

        logger.trace("Processing message for device discovery: CAN ID=0x{}",
                String.format("%08X", canMessage.getId().getFullValue()));

        try {
            IDSMessage idsMessage = IDSMessage.decode(canMessage);

            // Only process messages with valid source addresses (devices, not broadcast)
            Address sourceAddr = idsMessage.getSourceAddress();
            if (sourceAddr.isBroadcast()) {
                return;
            }

            // Track device from any message (DEVICE_STATUS, NETWORK, DEVICE_ID, etc.)
            DiscoveredDeviceInfo info = discoveredDevices.get(sourceAddr);
            if (info == null) {
                // New device discovered - track it
                info = new DiscoveredDeviceInfo(sourceAddr);
                discoveredDevices.put(sourceAddr, info);
                logger.debug("New device detected at address {} (from {} message)", sourceAddr.getValue(),
                        idsMessage.getMessageType().getName());

                // Request device ID for this new device
                requestDeviceID(sourceAddr);
            } else {
                info.updateLastSeen();
            }

            // Process DEVICE_ID messages to identify device type
            if (idsMessage.getMessageType() == MessageType.DEVICE_ID) {
                logger.debug("Processing DEVICE_ID message from device {}", sourceAddr.getValue());
                processDeviceID(sourceAddr, idsMessage.getData());
            }
        } catch (Exception e) {
            logger.debug("Error processing message for discovery: {}", e.getMessage());
        }
    }

    /**
     * Request device ID from a device.
     */
    private void requestDeviceID(Address deviceAddress) {
        IDSMyRVBridgeHandler bridge = bridgeHandler;
        if (bridge == null) {
            logger.debug("Cannot request device ID: bridge handler not set");
            return;
        }

        // Get source address from bridge configuration
        org.openhab.binding.idsmyrv.internal.config.BridgeConfiguration cfg = bridge.getBridgeConfiguration();
        if (cfg == null) {
            logger.debug("Cannot request device ID: bridge configuration not available");
            return;
        }

        Address sourceAddress = new Address(cfg.sourceAddress);

        try {
            CommandBuilder cmdBuilder = new CommandBuilder(sourceAddress, deviceAddress);
            CANMessage request = cmdBuilder.requestDeviceID();
            bridge.sendMessage(request);
            logger.debug("Requested device ID from device {}", deviceAddress.getValue());

            // Update last request time
            DiscoveredDeviceInfo info = discoveredDevices.get(deviceAddress);
            if (info != null) {
                info.lastIDRequest = System.currentTimeMillis();
            }
        } catch (Exception e) {
            logger.debug("Failed to request device ID from device {}: {}", deviceAddress.getValue(), e.getMessage());
        }
    }

    /**
     * Start initial device discovery when bridge comes online.
     * This waits a few seconds for devices to send messages, then requests device IDs.
     */
    private void startInitialDiscovery() {
        if (initialDiscoveryDone) {
            logger.debug("Initial discovery already done, skipping");
            return;
        }

        IDSMyRVBridgeHandler bridge = bridgeHandler;
        if (bridge == null || scheduler == null) {
            logger.debug("Cannot start initial discovery: bridge handler or scheduler not available");
            return;
        }

        // Check if bridge is online
        ThingStatus bridgeStatus = bridge.getThing().getStatus();
        if (bridgeStatus != ThingStatus.ONLINE) {
            logger.debug("Cannot start initial discovery: bridge is not online (status: {})", bridgeStatus);
            return;
        }

        logger.info("Starting active device discovery: waiting for connection to stabilize before querying devices...");

        // Cancel any existing initial discovery task
        if (initialDiscoveryDelayTask != null) {
            initialDiscoveryDelayTask.cancel(false);
        }

        // Wait 5 seconds after bridge comes online before scanning (matches Go control program behavior)
        // This gives the connection time to stabilize and allows devices to announce themselves naturally
        initialDiscoveryDelayTask = scheduler.schedule(() -> {
            IDSMyRVBridgeHandler currentBridge = bridgeHandler;
            if (currentBridge == null) {
                logger.debug("Bridge handler lost during initial discovery wait");
                return;
            }

            ThingStatus currentStatus = currentBridge.getThing().getStatus();
            if (currentStatus != ThingStatus.ONLINE) {
                logger.warn("Bridge went offline during initial discovery wait (status: {}), aborting discovery",
                        currentStatus);
                initialDiscoveryDone = false; // Reset so we can retry when bridge comes back online
                return;
            }

            // Now actively scan for devices by querying all possible addresses (0-254)
            // Address 255 (0xFF) is broadcast, so we skip it
            logger.info("Connection stabilized, scanning addresses 0-254 for devices...");
            scanForDevices();

            // Wait a bit more for responses, then request IDs from any devices that responded
            scheduler.schedule(() -> {
                IDSMyRVBridgeHandler finalBridge = bridgeHandler;
                if (finalBridge == null) {
                    logger.debug("Bridge handler lost during device ID request wait");
                    return;
                }

                ThingStatus finalStatus = finalBridge.getThing().getStatus();
                if (finalStatus != ThingStatus.ONLINE) {
                    logger.warn("Bridge went offline during device ID request wait (status: {}), aborting discovery",
                            finalStatus);
                    initialDiscoveryDone = false;
                    return;
                }

                logger.info(
                        "Initial discovery scan complete, found {} devices. Requesting device IDs for unidentified devices...",
                        discoveredDevices.size());

                // Request device IDs from all devices we've seen but haven't identified yet
                requestDeviceIDsForAllDevices();

                initialDiscoveryDone = true;
                logger.info("Initial device discovery complete");
            }, 3, TimeUnit.SECONDS);
        }, 5, TimeUnit.SECONDS);
    }

    /**
     * Actively scan for devices by querying all possible addresses (0-254).
     * Address 255 (0xFF) is broadcast, so we skip it.
     */
    private void scanForDevices() {
        IDSMyRVBridgeHandler bridge = bridgeHandler;
        if (bridge == null || scheduler == null) {
            logger.debug("Cannot scan for devices: bridge handler or scheduler not available");
            return;
        }

        // Check if bridge is online
        ThingStatus bridgeStatus = bridge.getThing().getStatus();
        if (bridgeStatus != ThingStatus.ONLINE) {
            logger.debug("Cannot scan for devices: bridge is not online (status: {})", bridgeStatus);
            return;
        }

        // Get source address from bridge configuration
        org.openhab.binding.idsmyrv.internal.config.BridgeConfiguration cfg = bridge.getBridgeConfiguration();
        if (cfg == null) {
            logger.debug("Cannot scan for devices: bridge configuration not available");
            return;
        }

        Address sourceAddress = new Address(cfg.sourceAddress);

        // Scan addresses 0-254 (255 is broadcast)
        int scanned = 0;
        for (int addr = 0; addr < 255; addr++) {
            final int address = addr;
            // Stagger requests to avoid flooding the bus
            scheduler.schedule(() -> {
                try {
                    Address targetAddr = new Address(address);
                    CommandBuilder cmdBuilder = new CommandBuilder(sourceAddress, targetAddr);
                    CANMessage request = cmdBuilder.requestDeviceID();
                    bridge.sendMessage(request);
                    logger.trace("Requested device ID from address {}", address);
                } catch (Exception e) {
                    logger.debug("Failed to request device ID from address {}: {}", address, e.getMessage());
                }
            }, scanned * 10, TimeUnit.MILLISECONDS); // 10ms delay between requests
            scanned++;
        }

        logger.info("Initiated device scan: sent DEVICE_ID requests to addresses 0-254 ({} requests)", scanned);
    }

    /**
     * Request device IDs from all devices that haven't been identified yet.
     */
    private void requestDeviceIDsForAllDevices() {
        IDSMyRVBridgeHandler bridge = bridgeHandler;
        if (bridge == null) {
            logger.debug("Cannot request device IDs: bridge handler not set");
            return;
        }

        // Check if bridge is online
        ThingStatus bridgeStatus = bridge.getThing().getStatus();
        if (bridgeStatus != ThingStatus.ONLINE) {
            logger.debug("Cannot request device IDs: bridge is not online (status: {})", bridgeStatus);
            return;
        }

        // Request device IDs from all devices we've seen but haven't identified yet
        long now = System.currentTimeMillis();
        int requested = 0;
        for (DiscoveredDeviceInfo info : discoveredDevices.values()) {
            // Request ID if we haven't received one yet, and haven't requested in the last 5 seconds
            if (!info.hasDeviceID && (now - info.lastIDRequest) > 5000) {
                logger.debug("Requesting device ID from device {} (seen but not identified)", info.address.getValue());
                requestDeviceID(info.address);
                requested++;
            }
        }

        if (requested > 0) {
            logger.info("Requested device IDs from {} unknown devices", requested);
        } else if (discoveredDevices.isEmpty()) {
            logger.info("No devices discovered yet - devices will be discovered as they send messages");
        } else {
            logger.info("All {} discovered devices already have IDs", discoveredDevices.size());
        }
    }

    /**
     * Start periodic device ID requests for devices that haven't been identified yet.
     * Used for manual scans.
     */
    private void startDeviceIDRequests() {
        requestDeviceIDsForAllDevices();
    }

    /**
     * Process a DEVICE_ID message to identify and discover a device.
     */
    private void processDeviceID(Address address, byte[] data) {
        if (data.length < 7) {
            logger.debug("DEVICE_ID message too short: {} bytes", data.length);
            return;
        }

        // Parse device type (byte 3 of DEVICE_ID message)
        int deviceTypeValue = data[3] & 0xFF;
        DeviceType deviceType = DeviceType.fromValue(deviceTypeValue);

        // Parse function name (bytes 4-5 of DEVICE_ID message, uint16 big-endian)
        int functionNameValue = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
        FunctionName functionName = FunctionName.fromValue(functionNameValue);

        // Parse device instance (upper 4 bits of byte 6)
        int deviceInstance = (data[6] >> 4) & 0x0F;

        // Parse device capabilities (byte 7, optional - may not be present)
        int deviceCapabilities = -1; // -1 means not available
        if (data.length >= 8) {
            deviceCapabilities = data[7] & 0xFF;
        }

        logger.debug("Device {} sent DEVICE_ID: type=0x{} ({}), function=0x{} ({}), instance={}, capabilities=0x{}",
                address.getValue(), String.format("%02X", deviceTypeValue), deviceType.getName(),
                String.format("%04X", functionNameValue), functionName.getName(), deviceInstance,
                deviceCapabilities >= 0 ? String.format("%02X", deviceCapabilities) : "N/A");

        // Get or create device info
        DiscoveredDeviceInfo info = discoveredDevices.get(address);
        if (info == null) {
            info = new DiscoveredDeviceInfo(address);
            discoveredDevices.put(address, info);
        }

        // Ignore devices with function name UNKNOWN (0x0000) - these are just open ports on the controller, not real
        // devices
        if (functionName == FunctionName.UNKNOWN || functionNameValue == 0) {
            logger.debug(
                    "Ignoring device {} with UNKNOWN function name (0x0000) - this is just an open port, not a real device",
                    address.getValue());
            return;
        }

        // Update device info with device ID, function name, instance, and capabilities
        info.setDeviceID(deviceType, functionName, deviceInstance, deviceCapabilities);
        info.updateLastSeen();

        // Only discover supported device types
        if (deviceType == DeviceType.UNKNOWN) {
            logger.debug("Device {} has unsupported device type 0x{}", address.getValue(),
                    String.format("%02X", deviceTypeValue));
            return;
        }

        logger.debug("Device {} identified: type={} (0x{}), function={} (0x{}), instance={}", address.getValue(),
                deviceType.getName(), String.format("%02X", deviceTypeValue), functionName.getName(),
                String.format("%04X", functionNameValue), deviceInstance);

        // Check if the thing still exists in the registry (it might have been deleted)
        // If it was deleted, reset the published flag to allow re-discovery
        // Only check once every 5 seconds to avoid repeated re-discovery attempts and reduce log noise
        boolean wasPublished = info.published; // Track if it was published before we check
        long now = System.currentTimeMillis();
        if (info.published && (now - info.lastRegistryCheck) > 5000) {
            ThingRegistry registry = thingRegistry;
            if (registry != null) {
                // Create thing UID to check if it exists
                IDSMyRVBridgeHandler bridge = bridgeHandler;
                if (bridge != null) {
                    ThingUID bridgeUID = bridge.getThing().getUID();
                    ThingTypeUID thingTypeUID = getThingTypeUID(deviceType);
                    if (thingTypeUID != null) {
                        ThingUID thingUID = new ThingUID(thingTypeUID, bridgeUID,
                                String.format("device_%d", address.getValue()));
                        org.openhab.core.thing.Thing existingThing = registry.get(thingUID);
                        info.lastRegistryCheck = now; // Update check time
                        if (existingThing == null) {
                            // Thing was deleted - reset published flag to allow re-discovery
                            logger.debug(
                                    "Device thing {} was previously published but no longer exists in registry, allowing re-discovery",
                                    thingUID);
                            info.published = false;
                        } else {
                            // Thing exists - make sure published flag is set
                            info.published = true;
                        }
                    }
                }
            }
        }

        // Publish discovery result if not already published
        if (!info.published) {
            // Distinguish between new discovery and re-discovery of deleted device
            if (wasPublished) {
                // This is a re-discovery of a previously deleted device
                logger.debug("Re-discovering previously deleted device: {} (type: {}, function: {})",
                        address.getValue(), deviceType.getName(), functionName.getName());
            } else {
                // This is a truly new device discovery
                logger.info("🔍 Discovered new device: {} (type: {}, function: {}) - attempting to publish",
                        address.getValue(), deviceType.getName(), functionName.getName());
            }
            publishDiscoveryResult(address, info);
            // Note: published flag is set inside publishDiscoveryResult() after successful publish
            // or if the thing already exists in the registry
        } else {
            logger.trace("Device {} already published, skipping", address.getValue());
        }
    }

    /**
     * Get the ThingTypeUID for a given DeviceType.
     *
     * @param deviceType The device type
     * @return The ThingTypeUID, or null if the device type is not supported
     */
    private @Nullable ThingTypeUID getThingTypeUID(DeviceType deviceType) {
        if (deviceType == DeviceType.DIMMABLE_LIGHT) {
            return IDSMyRVBindingConstants.THING_TYPE_LIGHT;
        } else if (deviceType == DeviceType.RGB_LIGHT) {
            return IDSMyRVBindingConstants.THING_TYPE_RGB_LIGHT;
        } else if (deviceType == DeviceType.TANK_SENSOR) {
            return IDSMyRVBindingConstants.THING_TYPE_TANK_SENSOR;
        } else if (deviceType == DeviceType.LATCHING_RELAY || deviceType == DeviceType.LATCHING_RELAY_TYPE_2) {
            return IDSMyRVBindingConstants.THING_TYPE_LATCHING_RELAY;
        } else if (deviceType == DeviceType.MOMENTARY_H_BRIDGE || deviceType == DeviceType.MOMENTARY_H_BRIDGE_T2) {
            return IDSMyRVBindingConstants.THING_TYPE_MOMENTARY_H_BRIDGE;
        } else if (deviceType == DeviceType.HVAC_CONTROL) {
            return IDSMyRVBindingConstants.THING_TYPE_HVAC;
        } else {
            return null;
        }
    }

    /**
     * Publish a discovery result for a device.
     */
    private void publishDiscoveryResult(Address address, DiscoveredDeviceInfo info) {
        DeviceType deviceType = info.deviceType;
        FunctionName functionName = info.functionName;
        IDSMyRVBridgeHandler bridge = bridgeHandler;
        if (bridge == null) {
            logger.warn("Cannot publish discovery result: bridge handler not set");
            return;
        }

        ThingUID bridgeUID = bridge.getThing().getUID();

        // Determine thing type based on device type
        ThingTypeUID thingTypeUID = getThingTypeUID(deviceType);
        if (thingTypeUID == null) {
            logger.warn("No thing type mapping for device type {} (value: 0x{}) - device will not be published",
                    deviceType.getName(), String.format("%02X", deviceType.getValue()));
            return;
        }

        // Create thing UID
        ThingUID thingUID = new ThingUID(thingTypeUID, bridgeUID, String.format("device_%d", address.getValue()));

        // Single registry check: verify the thing doesn't exist before publishing
        // This prevents rediscovering devices that have already been added
        ThingRegistry registry = thingRegistry;
        if (registry != null) {
            org.openhab.core.thing.Thing existingThing = registry.get(thingUID);
            if (existingThing != null) {
                // Thing exists in registry - don't publish
                logger.debug("Device thing {} already exists in registry, skipping discovery result", thingUID);
                info.published = true;
                return;
            }
        }

        logger.debug("Publishing discovery result for device {}: deviceType={}, thingTypeUID={}", address.getValue(),
                deviceType.getName(), thingTypeUID);

        // Build discovery result
        // Properties must match the configuration parameter types exactly:
        // - integer -> Integer (not int primitive)
        Map<String, Object> properties = new HashMap<>();
        properties.put(IDSMyRVBindingConstants.CONFIG_DEVICE_ADDRESS, Integer.valueOf(address.getValue()));

        // Device type properties (numeric and human-readable)
        properties.put(IDSMyRVBindingConstants.PROPERTY_DEVICE_TYPE, Integer.valueOf(deviceType.getValue()));
        properties.put(IDSMyRVBindingConstants.PROPERTY_DEVICE_TYPE_NAME, deviceType.getName());

        // Function class and device name (from function name if available)
        String functionClassName = null;
        String deviceName = null;
        if (functionName != null && functionName != FunctionName.UNKNOWN
                && !functionName.getName().startsWith("Unknown")) {
            functionClassName = functionName.getName();
            deviceName = functionName.getName();
        } else {
            // Fall back to device type if function name is not available
            functionClassName = deviceType.getName();
            deviceName = deviceType.getName();
        }
        properties.put(IDSMyRVBindingConstants.PROPERTY_FUNCTION_CLASS, functionClassName);
        properties.put(IDSMyRVBindingConstants.PROPERTY_DEVICE_NAME, deviceName);

        // Device instance
        properties.put(IDSMyRVBindingConstants.PROPERTY_INSTANCE, Integer.valueOf(info.deviceInstance));

        // Device capabilities (if available)
        if (info.deviceCapabilities >= 0) {
            properties.put(IDSMyRVBindingConstants.PROPERTY_DEVICE_CAPABILITIES,
                    Integer.valueOf(info.deviceCapabilities));
        }

        // Use function name if available and not UNKNOWN, otherwise fall back to device type
        String label;
        if (functionName != null && functionName != FunctionName.UNKNOWN
                && !functionName.getName().startsWith("Unknown")) {
            label = functionName.getName();
        } else {
            // Fall back to device type (address is already in ThingUID, so no need to include it in label)
            label = deviceType.getName();
        }

        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withThingType(thingTypeUID)
                .withBridge(bridgeUID).withProperties(properties).withLabel(label)
                .withRepresentationProperty(IDSMyRVBindingConstants.CONFIG_DEVICE_ADDRESS).build();

        try {
            logger.debug("Calling thingDiscovered() for device {} (thingUID: {})", address.getValue(), thingUID);
            thingDiscovered(discoveryResult);
            // Only log at INFO level for truly new discoveries, use DEBUG for re-discoveries
            // Note: OpenHAB may silently ignore duplicate discovery results, so this log
            // doesn't guarantee the thing was actually added to the inbox
            logger.debug("Published discovery result for {}: {} (thingUID: {})", deviceType.getName(), label, thingUID);
            // Mark as published only after successful publish
            info.published = true;
        } catch (Exception e) {
            logger.error("❌ Failed to publish discovery result for {} (thingUID: {}): {}", deviceType.getName(),
                    thingUID, e.getMessage(), e);
            // Don't mark as published if there was an error - we can retry later
        }
    }
}
