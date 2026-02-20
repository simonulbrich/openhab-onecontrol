package org.openhab.binding.idsmyrv.internal.discovery;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.idsmyrv.internal.IDSMyRVBindingConstants;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovery service for IDS MyRV CAN gateways on the network.
 *
 * Discovers gateways by listening to UDP broadcasts on port 47664.
 * Gateways broadcast JSON announcements that are parsed and added to the inbox.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
@Component(service = { org.openhab.core.config.discovery.DiscoveryService.class,
        AbstractDiscoveryService.class }, immediate = true)
public class IDSMyRVGatewayDiscoveryService extends AbstractDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(IDSMyRVGatewayDiscoveryService.class);

    private static final int DISCOVERY_TIMEOUT_SECONDS = 30;

    private ScheduledExecutorService scheduler;
    private boolean schedulerOwned = false; // Track if we created our own scheduler
    private @Nullable DiscoveryService udpDiscoveryService;
    private @Nullable ScheduledFuture<?> discoveryTask;
    private @Nullable ScheduledFuture<?> initialDiscoveryTimer; // Timer to stop discovery after 30 seconds
    private boolean manualScanActive = false; // Track if a manual scan is active

    // Track discovered gateways to avoid duplicate discovery results
    private final Set<String> discoveredGateways = Collections.synchronizedSet(new HashSet<>());

    private @Nullable ThingRegistry thingRegistry;

    public IDSMyRVGatewayDiscoveryService() {
        super(Collections.unmodifiableSet(createSupportedThingTypes()), DISCOVERY_TIMEOUT_SECONDS, true);
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
        logger.debug("ScheduledExecutorService injected");
    }

    protected void unsetScheduler(ScheduledExecutorService scheduler) {
        if (this.scheduler == scheduler) {
            this.scheduler = null;
        }
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void setThingRegistry(ThingRegistry thingRegistry) {
        this.thingRegistry = thingRegistry;
        logger.debug("ThingRegistry injected");
    }

    protected void unsetThingRegistry(ThingRegistry thingRegistry) {
        if (this.thingRegistry == thingRegistry) {
            this.thingRegistry = null;
        }
    }

    private static Set<ThingTypeUID> createSupportedThingTypes() {
        Set<ThingTypeUID> types = new HashSet<>();
        types.add(IDSMyRVBindingConstants.THING_TYPE_BRIDGE);
        return types;
    }

    @Override
    @Activate
    protected void activate(@Nullable Map<String, @Nullable Object> configProperties) {
        super.activate(configProperties);
        logger.info("Gateway discovery service activated (background discovery enabled: {})",
                isBackgroundDiscoveryEnabled());

        // Ensure we have a scheduler
        if (scheduler == null) {
            scheduler = java.util.concurrent.Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "IDSMyRV-GatewayDiscovery");
                t.setDaemon(true);
                return t;
            });
            schedulerOwned = true;
            logger.debug("Created own ScheduledExecutorService");
        }

        // Start UDP discovery service for initial discovery period
        if (isBackgroundDiscoveryEnabled()) {
            startDiscoveryService();

            // Schedule discovery to stop after 30 seconds (unless a manual scan is active)
            if (scheduler != null) {
                initialDiscoveryTimer = scheduler.schedule(() -> {
                    if (!manualScanActive) {
                        logger.info("Initial discovery period (30 seconds) expired, stopping discovery service");
                        stopDiscoveryService();
                    } else {
                        logger.debug(
                                "Initial discovery period expired, but manual scan is active - keeping discovery running");
                    }
                }, 30, TimeUnit.SECONDS);
            }
        }
    }

    @Override
    @Deactivate
    protected void deactivate() {
        logger.info("Gateway discovery service deactivating");

        // Cancel initial discovery timer
        ScheduledFuture<?> timer = initialDiscoveryTimer;
        if (timer != null) {
            timer.cancel(false);
            initialDiscoveryTimer = null;
        }

        // Stop periodic discovery
        ScheduledFuture<?> task = discoveryTask;
        if (task != null) {
            task.cancel(false);
            discoveryTask = null;
        }

        // Stop UDP discovery service
        stopDiscoveryService();

        // Shutdown our own scheduler if we created it
        if (schedulerOwned && scheduler != null) {
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
            schedulerOwned = false;
            logger.debug("Shutdown own ScheduledExecutorService");
        }

        discoveredGateways.clear();
        super.deactivate();
        logger.info("Gateway discovery service deactivated");
    }

    @Override
    protected void startScan() {
        logger.info("Starting manual gateway discovery scan");
        manualScanActive = true;

        // Start the UDP discovery service if not already running
        startDiscoveryService();

        // Start periodic check for new gateways
        startGatewayDiscovery();
    }

    @Override
    protected synchronized void stopScan() {
        logger.debug("Stopping gateway discovery scan");
        manualScanActive = false;

        // Stop periodic check
        ScheduledFuture<?> task = discoveryTask;
        if (task != null) {
            task.cancel(false);
            discoveryTask = null;
        }

        // Stop UDP discovery service only if manual scan was active
        // (background discovery will stop automatically after 5 minutes)
        stopDiscoveryService();

        super.stopScan();
    }

    /**
     * Start the UDP discovery service.
     */
    private synchronized void startDiscoveryService() {
        if (udpDiscoveryService != null) {
            logger.debug("UDP discovery service already running");
            return;
        }

        if (scheduler == null) {
            logger.warn("Cannot start discovery service: scheduler not available");
            return;
        }

        try {
            DiscoveryService discovery = new DiscoveryService(scheduler);
            // Set listener to be notified immediately when gateways are discovered
            discovery.setListener(this::onGatewayDiscovered);
            discovery.start();
            this.udpDiscoveryService = discovery;
            logger.info("UDP discovery service started on port 47664");
        } catch (Exception e) {
            logger.error("Failed to start UDP discovery service: {}", e.getMessage(), e);
        }
    }

    /**
     * Stop the UDP discovery service.
     */
    private synchronized void stopDiscoveryService() {
        DiscoveryService discovery = udpDiscoveryService;
        if (discovery != null) {
            discovery.setListener(null); // Remove listener
            discovery.stop();
            udpDiscoveryService = null;
            logger.info("UDP discovery service stopped");
        }
    }

    /**
     * Start periodic gateway discovery.
     */
    private void startGatewayDiscovery() {
        if (scheduler == null) {
            logger.warn("Cannot start gateway discovery: scheduler not available");
            return;
        }

        ScheduledFuture<?> task = discoveryTask;
        if (task != null && !task.isDone()) {
            return; // Already running
        }

        // Check for gateways immediately
        checkForGateways();

        // Then check periodically every 5 seconds
        discoveryTask = scheduler.scheduleWithFixedDelay(this::checkForGateways, 5, 5, TimeUnit.SECONDS);
        logger.debug("Started periodic gateway discovery (every 5 seconds)");
    }

    /**
     * Callback when a new gateway is discovered via UDP.
     * This is called immediately when the UDP discovery service receives an announcement.
     *
     * @param gateway The discovered gateway information
     */
    private void onGatewayDiscovered(DiscoveryService.GatewayInfo gateway) {
        // Publish if background discovery is enabled OR if a manual scan is active
        if (!isBackgroundDiscoveryEnabled() && !manualScanActive) {
            logger.debug(
                    "Background discovery is disabled and no manual scan active, ignoring gateway discovery notification");
            return;
        }

        String gatewayKey = gateway.address + ":" + gateway.port;
        if (!discoveredGateways.contains(gatewayKey)) {
            logger.info("🔍 New gateway discovered via UDP: {} at {}:{}", gateway.name, gateway.address, gateway.port);
            publishGateway(gateway);
            discoveredGateways.add(gatewayKey);
        } else {
            logger.trace("Gateway {} already published, ignoring notification", gatewayKey);
        }
    }

    /**
     * Check for newly discovered gateways and publish discovery results.
     * This is a periodic backup check in case we miss UDP notifications.
     */
    private void checkForGateways() {
        // Check if background discovery is enabled OR if a manual scan is active
        if (!isBackgroundDiscoveryEnabled() && !manualScanActive) {
            logger.debug("Background discovery is disabled and no manual scan active, skipping gateway check");
            return;
        }

        DiscoveryService discovery = udpDiscoveryService;
        if (discovery == null) {
            logger.debug("UDP discovery service not available");
            return;
        }

        try {
            // Get all discovered gateways
            java.util.Map<String, DiscoveryService.GatewayInfo> allGateways = discovery.getAllGateways();
            logger.debug("Checking {} discovered gateways", allGateways.size());

            // Check each gateway and publish if not already discovered
            for (DiscoveryService.GatewayInfo gateway : allGateways.values()) {
                String gatewayKey = gateway.address + ":" + gateway.port;
                if (!discoveredGateways.contains(gatewayKey)) {
                    logger.info("🔍 New gateway discovered: {} at {}:{}", gateway.name, gateway.address, gateway.port);
                    publishGateway(gateway);
                    discoveredGateways.add(gatewayKey);
                } else {
                    logger.trace("Gateway {} already published, skipping", gatewayKey);
                }
            }
        } catch (Exception e) {
            logger.warn("Error checking for gateways: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish a discovery result for a gateway.
     */
    private void publishGateway(DiscoveryService.GatewayInfo gateway) {
        ThingTypeUID thingTypeUID = IDSMyRVBindingConstants.THING_TYPE_BRIDGE;

        // Create thing UID using gateway address as the ID
        // ThingUID IDs must be alphanumeric with underscores/hyphens, and not start with a number
        // Format: remove dots from IP address (e.g., 192.168.1.1 -> 19216811)
        String gatewayId = gateway.address.replace(".", "").replaceAll("[^a-zA-Z0-9_-]", "");
        // Ensure it doesn't start with a number (ThingUID requirement)
        if (gatewayId.isEmpty() || gatewayId.matches("^[0-9].*")) {
            gatewayId = "gateway" + gatewayId;
        }
        ThingUID thingUID = new ThingUID(thingTypeUID, gatewayId);

        logger.debug("Generated ThingUID: {} from address: {}", thingUID, gateway.address);

        // Check if this thing already exists in the registry
        ThingRegistry registry = thingRegistry;
        if (registry != null) {
            org.openhab.core.thing.Thing existingThing = registry.get(thingUID);
            if (existingThing != null) {
                logger.debug("Gateway thing {} already exists in registry (status: {}), skipping discovery result",
                        thingUID, existingThing.getStatus());
                return; // Don't publish discovery result for existing things
            }
        } else {
            logger.trace("ThingRegistry not available, cannot check for existing things");
        }

        logger.debug("Creating discovery result for gateway: address={}, port={}, name={}, thingUID={}",
                gateway.address, gateway.port, gateway.name, thingUID);

        // Build discovery result
        // Properties must match the configuration parameter types exactly:
        // - text -> String
        // - integer -> Integer (not int primitive)
        // - boolean -> Boolean (not boolean primitive)
        // Only include discovered properties + required properties (not optional ones with defaults)
        Map<String, Object> properties = new HashMap<>();
        properties.put(IDSMyRVBindingConstants.CONFIG_IP_ADDRESS, gateway.address); // String for text type - discovered
        properties.put(IDSMyRVBindingConstants.CONFIG_PORT, Integer.valueOf(gateway.port)); // Integer for integer type
                                                                                            // - discovered
        properties.put(IDSMyRVBindingConstants.CONFIG_SOURCE_ADDRESS, Integer.valueOf(1)); // Integer for integer type -
                                                                                           // required, use default
        // The user can change it later if needed

        // Create label - use name if available and not "NoName", otherwise use IP address
        String label;
        if (gateway.name != null && !gateway.name.isEmpty() && !"NoName".equalsIgnoreCase(gateway.name)) {
            label = String.format("IDS MyRV Gateway - %s", gateway.name);
        } else {
            label = String.format("IDS MyRV Gateway - %s", gateway.address);
        }

        try {
            // Verify the thing type is supported
            if (!getSupportedThingTypes().contains(thingTypeUID)) {
                logger.warn("Thing type {} is not in supported types: {}", thingTypeUID, getSupportedThingTypes());
                return;
            }

            DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withThingType(thingTypeUID)
                    .withProperties(properties).withLabel(label)
                    .withRepresentationProperty(IDSMyRVBindingConstants.CONFIG_IP_ADDRESS).build();

            logger.debug("Discovery result created: thingUID={}, label={}, properties={}, representationProperty={}",
                    thingUID, label, properties, IDSMyRVBindingConstants.CONFIG_IP_ADDRESS);

            // Publish the discovery result
            logger.debug("Calling thingDiscovered() with result: thingUID={}, label={}", thingUID, label);
            thingDiscovered(discoveryResult);

            // Verify it was actually added (this is a best-effort check)
            logger.info("✅ Published gateway discovery result: {} at {}:{} (thingUID: {})", gateway.name,
                    gateway.address, gateway.port, thingUID);
            logger.debug("Background discovery enabled: {}, supported thing types: {}", isBackgroundDiscoveryEnabled(),
                    getSupportedThingTypes());
        } catch (IllegalArgumentException e) {
            logger.error("❌ Invalid discovery result parameters: {}", e.getMessage(), e);
            logger.error("   thingUID: {}, properties: {}", thingUID, properties);
        } catch (IllegalStateException e) {
            logger.error("❌ Discovery service not in valid state: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("❌ Failed to publish gateway discovery result: {}", e.getMessage(), e);
            logger.error("   Exception type: {}, thingUID: {}", e.getClass().getName(), thingUID);
        }
    }
}
