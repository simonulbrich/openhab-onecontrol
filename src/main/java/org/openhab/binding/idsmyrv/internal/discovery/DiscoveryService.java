package org.openhab.binding.idsmyrv.internal.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for discovering CAN gateways on the network via UDP broadcasts.
 *
 * Gateways broadcast JSON announcements on UDP port 47664:
 * {
 * "mfg": "IDS",
 * "product": "CAN_TO_ETHERNET_GATEWAY",
 * "name": "Gateway-Name",
 * "port": "6969"
 * }
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class DiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryService.class);

    private static final int DISCOVERY_PORT = 47664;
    private static final long EXPIRY_TIME_MS = 5000; // 5 seconds
    private static final long CLEANUP_INTERVAL_MS = 1000; // 1 second

    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, GatewayInfo> gateways = new ConcurrentHashMap<>();

    private @Nullable DatagramSocket socket;
    // Note: Future<?> cannot be annotated with @Nullable due to generic wildcard
    private java.util.concurrent.Future<?> listenerTask;
    private @Nullable ScheduledFuture<?> cleanupTask;

    /**
     * Callback interface for gateway discovery events.
     */
    public interface GatewayDiscoveryListener {
        /**
         * Called when a new gateway is discovered.
         *
         * @param gateway The discovered gateway information
         */
        void onGatewayDiscovered(GatewayInfo gateway);
    }

    private @Nullable GatewayDiscoveryListener listener;

    public DiscoveryService(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Set a listener to be notified when new gateways are discovered.
     *
     * @param listener The listener to notify, or null to remove
     */
    public void setListener(@Nullable GatewayDiscoveryListener listener) {
        this.listener = listener;
    }

    /**
     * Information about a discovered gateway.
     */
    public static class GatewayInfo {
        public final String address;
        public final int port;
        public final String name;
        public final String manufacturer;
        public final String product;
        public final Instant lastSeen;

        public GatewayInfo(String address, int port, String name, String manufacturer, String product) {
            this.address = address;
            this.port = port;
            this.name = name;
            this.manufacturer = manufacturer;
            this.product = product;
            this.lastSeen = Instant.now();
        }

        public boolean isExpired() {
            return Instant.now().toEpochMilli() - lastSeen.toEpochMilli() > EXPIRY_TIME_MS;
        }

        @Override
        public String toString() {
            return String.format("%s (%s:%d) - %s %s", name, address, port, manufacturer, product);
        }
    }

    /**
     * Start the discovery service.
     *
     * @throws IOException if the UDP socket cannot be created
     */
    public synchronized void start() throws IOException {
        if (running.get()) {
            logger.debug("Discovery service already running");
            return;
        }

        try {
            // Create unbound socket first (matches Go net.ListenUDP behavior)
            DatagramSocket newSocket = new DatagramSocket(null);
            // Enable SO_REUSEADDR to allow binding even if port is in TIME_WAIT state
            newSocket.setReuseAddress(true);
            // Bind to all interfaces (0.0.0.0) on the discovery port (matches Go net.IPv4zero)
            SocketAddress bindAddr = new InetSocketAddress(InetAddress.getByName("0.0.0.0"), DISCOVERY_PORT);
            newSocket.bind(bindAddr);
            newSocket.setSoTimeout(1000); // 1 second timeout for periodic checks (matches Go SetReadDeadline)
            this.socket = newSocket;

            running.set(true);
            InetSocketAddress localAddr = (InetSocketAddress) newSocket.getLocalSocketAddress();
            logger.info("Discovery service started on UDP port {} (bound to {})", DISCOVERY_PORT,
                    localAddr != null ? localAddr.getAddress().getHostAddress() : "unknown");
            logger.debug("Socket details: localAddress={}, localPort={}, reuseAddress={}",
                    localAddr != null ? localAddr.getAddress() : "null", localAddr != null ? localAddr.getPort() : -1,
                    newSocket.getReuseAddress());

            // Start listener task
            listenerTask = scheduler.submit(this::listenLoop);

            // Start cleanup task
            cleanupTask = scheduler.scheduleWithFixedDelay(this::cleanupExpired, CLEANUP_INTERVAL_MS,
                    CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
        } catch (IOException e) {
            logger.error("Failed to start discovery service on port {}: {}", DISCOVERY_PORT, e.getMessage());
            throw e;
        }
    }

    /**
     * Stop the discovery service.
     */
    public synchronized void stop() {
        if (!running.get()) {
            return;
        }

        running.set(false);

        if (listenerTask != null) {
            listenerTask.cancel(true);
            listenerTask = null;
        }

        if (cleanupTask != null) {
            cleanupTask.cancel(true);
            cleanupTask = null;
        }

        DatagramSocket s = socket;
        if (s != null) {
            s.close();
            socket = null;
        }

        gateways.clear();
        logger.info("Discovery service stopped");
    }

    /**
     * Get all currently discovered (non-expired) gateways.
     *
     * @return Array of gateway info
     */
    public GatewayInfo[] getGateways() {
        return gateways.values().stream().filter(gw -> !gw.isExpired()).toArray(GatewayInfo[]::new);
    }

    /**
     * Wait for a gateway to be discovered.
     *
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return The first discovered gateway, or null if timeout
     */
    @Nullable
    public GatewayInfo waitForGateway(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;

        logger.debug("Waiting up to {} seconds for gateway discovery...", timeoutMs / 1000);

        while (System.currentTimeMillis() < deadline && running.get()) {
            GatewayInfo[] gateways = getGateways();
            if (gateways.length > 0) {
                return gateways[0];
            }

            try {
                Thread.sleep(100); // Check every 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        logger.debug("Gateway discovery timeout: No gateway found after {} seconds", timeoutMs / 1000);
        return null;
    }

    /**
     * Main listener loop for receiving UDP announcements.
     */
    private void listenLoop() {
        byte[] buffer = new byte[1024];
        int packetsReceived = 0;

        logger.debug("Discovery listener loop started");

        while (running.get()) {
            DatagramSocket s = socket;
            if (s == null) {
                logger.debug("Socket is null, stopping listener loop");
                break;
            }

            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                s.receive(packet);

                packetsReceived++;
                logger.debug("Received UDP packet #{} from {}:{} ({} bytes)", packetsReceived,
                        packet.getAddress().getHostAddress(), packet.getPort(), packet.getLength());

                if (packet.getLength() > 0) {
                    processAnnouncement(packet);
                }
            } catch (SocketTimeoutException e) {
                // Expected - continue listening
                continue;
            } catch (IOException e) {
                if (running.get()) {
                    logger.warn("UDP read error: {}", e.getMessage());
                }
                // Continue listening
            }
        }

        logger.debug("Discovery listener loop stopped (received {} packets total)", packetsReceived);
    }

    /**
     * Process a received announcement packet.
     */
    private void processAnnouncement(DatagramPacket packet) {
        try {
            String json = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            logger.debug("Received announcement JSON from {}: {}", packet.getAddress().getHostAddress(), json);

            // Simple JSON parser for fixed structure: {"mfg":"...","product":"...","name":"...","port":"..."}
            String manufacturer = extractJsonString(json, "mfg");
            String product = extractJsonString(json, "product");

            logger.debug("Parsed: mfg={}, product={}", manufacturer, product);

            // Only process IDS CAN-to-Ethernet gateways
            if (manufacturer == null || product == null) {
                logger.debug("Missing mfg or product in announcement from {}", packet.getAddress().getHostAddress());
                return;
            }

            if (!"IDS".equals(manufacturer) || !"CAN_TO_ETHERNET_GATEWAY".equals(product)) {
                logger.debug("Ignoring announcement from non-IDS gateway: mfg={}, product={}", manufacturer, product);
                return;
            }

            String name = extractJsonString(json, "name");
            String portStr = extractJsonString(json, "port");

            if (name == null || portStr == null) {
                logger.warn("Invalid announcement format from {}: missing name or port (name={}, port={})",
                        packet.getAddress().getHostAddress(), name, portStr);
                return;
            }

            int port = Integer.parseInt(portStr);
            String address = packet.getAddress().getHostAddress();

            GatewayInfo info = new GatewayInfo(address, port, name, manufacturer, product);

            GatewayInfo existing = gateways.put(address, info);
            if (existing == null) {
                logger.info("Discovered gateway: {}", info);
                // Notify listener if this is a new gateway
                GatewayDiscoveryListener l = listener;
                if (l != null) {
                    try {
                        l.onGatewayDiscovered(info);
                    } catch (Exception e) {
                        logger.warn("Error notifying gateway discovery listener: {}", e.getMessage());
                    }
                }
            } else {
                logger.debug("Updated gateway: {}", info);
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to parse announcement from {}: {}", packet.getAddress().getHostAddress(),
                    e.getMessage());
        }
    }

    /**
     * Extract a string value from a simple JSON object.
     * Handles quoted strings only.
     */
    @Nullable
    private String extractJsonString(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) {
            return null;
        }

        int colonIndex = json.indexOf(':', keyIndex);
        if (colonIndex == -1) {
            return null;
        }

        // Find the opening quote
        int quoteStart = json.indexOf('"', colonIndex);
        if (quoteStart == -1) {
            return null;
        }

        // Find the closing quote
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd == -1) {
            return null;
        }

        return json.substring(quoteStart + 1, quoteEnd);
    }

    /**
     * Get all currently discovered gateways.
     *
     * @return A copy of the map of discovered gateways (address -> GatewayInfo)
     */
    public java.util.Map<String, GatewayInfo> getAllGateways() {
        return new java.util.HashMap<>(gateways);
    }

    /**
     * Clean up expired gateways.
     */
    private void cleanupExpired() {
        gateways.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                logger.info("Gateway expired: {}", entry.getValue());
                return true;
            }
            return false;
        });
    }
}
