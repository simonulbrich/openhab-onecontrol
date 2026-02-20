package org.openhab.binding.idsmyrv.internal.gateway;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;
import org.openhab.binding.idsmyrv.internal.can.CANID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tel.schich.javacan.CanChannels;
import tel.schich.javacan.CanFrame;
import tel.schich.javacan.NetworkDevice;
import tel.schich.javacan.RawCanChannel;

/**
 * Client for direct CAN bus communication using SocketCAN on Linux.
 *
 * Features:
 * - Direct SocketCAN interface (e.g., can0, vcan0)
 * - Automatic reconnection on errors
 * - Thread-safe message sending
 * - Callback-based message handling
 * - Periodic keepalive (NETWORK broadcast)
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class SocketCANClient implements CANConnection {
    private final Logger logger = LoggerFactory.getLogger(SocketCANClient.class);

    private static final int RECONNECT_DELAY_MS = 2000;
    private static final int KEEPALIVE_INTERVAL_MS = 1000; // Send NETWORK broadcast every 1 second

    private final String interfaceName;
    private final CANConnection.MessageHandler messageHandler;
    private final ScheduledExecutorService scheduler;
    private final boolean verboseLogging;
    private final int sourceAddress;

    private @Nullable RawCanChannel channel;
    private @Nullable Thread readerThread;
    private @Nullable ScheduledFuture<?> keepaliveTask;

    private volatile boolean running = false;
    private volatile boolean connected = false;
    private volatile boolean shouldStop = false;

    /**
     * Create a new SocketCAN client.
     *
     * @param interfaceName The CAN interface name (e.g., "can0", "vcan0")
     * @param messageHandler Callback for received messages
     * @param scheduler Executor for async operations
     * @param verboseLogging Enable verbose logging
     * @param sourceAddress The source address to use for NETWORK broadcasts (1-254)
     */
    public SocketCANClient(String interfaceName, CANConnection.MessageHandler messageHandler,
            ScheduledExecutorService scheduler, boolean verboseLogging, int sourceAddress) {
        this.interfaceName = interfaceName;
        this.messageHandler = messageHandler;
        this.scheduler = scheduler;
        this.verboseLogging = verboseLogging;
        this.sourceAddress = sourceAddress;
    }

    /**
     * Connect to the CAN interface.
     *
     * @throws IOException if connection fails
     */
    @Override
    public synchronized void connect() throws IOException {
        // Prevent multiple simultaneous connections
        if (running || readerThread != null) {
            if (verboseLogging) {
                logger.debug("Already running or reader thread exists, skipping connect");
            }
            return;
        }

        if (verboseLogging) {
            logger.debug("Opening SocketCAN interface {}...", interfaceName);
        }

        try {
            // Open the SocketCAN interface
            NetworkDevice device = NetworkDevice.lookup(interfaceName);
            logger.info("📡 Opening RawCanChannel for device: {}", device.getName());
            
            RawCanChannel newChannel = CanChannels.newRawChannel(device);
            logger.info("📡 RawCanChannel created, now binding...");
            
            // CRITICAL: Bind the channel to enable TX/RX
            newChannel.bind(device);
            logger.info("✅ Channel bound to device: {}", device.getName());
            
            // Configure channel for blocking reads
            newChannel.configureBlocking(true);
            logger.info("✅ Channel configured for blocking reads");
            
            // DON'T set any filter - by default it should receive all frames
            // Setting an empty filter array blocks everything!
            logger.info("SocketCAN channel opened, blocking mode enabled, using default filter (accept all)");

            this.channel = newChannel;
            this.running = true;
            this.connected = true;
            this.shouldStop = false;

            // Start reader thread if not already started
            if (readerThread == null || !readerThread.isAlive()) {
                startReaderThread();
            }

            // Start periodic NETWORK broadcast to maintain presence on the bus
            startKeepalive();

            logger.info("Connected to SocketCAN interface {}", interfaceName);
        } catch (Exception e) {
            throw new IOException("Failed to open SocketCAN interface " + interfaceName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Start the reader thread that processes incoming CAN frames.
     * Handles reconnection internally on errors.
     */
    private synchronized void startReaderThread() {
        // Prevent multiple reader threads
        if (readerThread != null && readerThread.isAlive()) {
            if (verboseLogging) {
                logger.debug("Reader thread already running, not starting another");
            }
            return;
        }

        readerThread = new Thread(() -> {
            ByteBuffer readBuffer = ByteBuffer.allocateDirect(16); // Standard CAN frame size
            readBuffer.order(ByteOrder.nativeOrder()); // Set to native byte order (LITTLE_ENDIAN on x86)
            
            logger.info("SocketCAN reader thread started for interface {}", interfaceName);
            int loopCount = 0;

            while (!shouldStop) {
                loopCount++;
                if (loopCount % 100 == 0) {
                    logger.debug("Reader thread loop iteration {}, connected={}", loopCount, connected);
                }
                // Check if connected, if not try to reconnect
                if (!connected || channel == null) {
                    if (!shouldStop) {
                        if (verboseLogging) {
                            logger.debug("Not connected, attempting reconnect in {}ms", RECONNECT_DELAY_MS);
                        }
                        try {
                            Thread.sleep(RECONNECT_DELAY_MS);
                            if (!shouldStop && !connected) {
                                logger.debug("Attempting to reconnect...");
                                try {
                                    reconnectInternal();
                                } catch (IOException e) {
                                    logger.warn("Reconnect failed: {}", e.getMessage());
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    continue;
                }

                try {
                    RawCanChannel ch = this.channel;
                    if (ch == null) {
                        logger.debug("Channel is null in reader loop, skipping read");
                        Thread.sleep(100); // Avoid busy loop
                        continue;
                    }

                    // Read CAN frame - this blocks until a frame is received and returns the frame directly!
                    CanFrame frame = ch.read();
                    
                    if (frame == null) {
                        logger.warn("Received null frame from ch.read(), skipping");
                        continue;
                    }

                    // Convert JavaCAN frame to our CANMessage
                    CANMessage message = convertToCANMessage(frame);

                    // Log received message only in verbose mode
                    if (verboseLogging) {
                        StringBuilder hex = new StringBuilder();
                        for (byte b : message.getData()) {
                            hex.append(String.format("%02X ", b));
                        }
                        logger.debug("RX CAN: ID=0x{} len={} data=[{}]", 
                                String.format("%X", message.getId().getRaw()),
                                message.getData().length,
                                hex.toString().trim());
                    }

                    messageHandler.handleMessage(message);

                } catch (IOException e) {
                    logger.warn("❌ SocketCAN I/O error: {}", e.getMessage(), e);
                    synchronized (this) {
                        connected = false;
                        running = false;
                        closeChannel();
                    }
                    // Don't break - loop will try to reconnect
                    continue;
                } catch (Exception e) {
                    logger.warn("❌ Unexpected exception in reader loop {}: {}", loopCount, e.getMessage(), e);
                    // Continue reading despite error
                }
            }

            if (verboseLogging) {
                logger.debug("Reader thread stopped");
            }
        }, "SocketCANClient-Reader");

        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Convert a JavaCAN CanFrame to our internal CANMessage format.
     */
    private CANMessage convertToCANMessage(CanFrame frame) {
        int canId = frame.getId();
        boolean isExtended = frame.isExtended();
        byte[] data = new byte[frame.getDataLength()];
        frame.getData(data, 0, data.length);

        // Create CANID using factory methods
        CANID id;
        if (isExtended) {
            id = CANID.extended(canId);
        } else {
            id = CANID.standard(canId);
        }

        // Create CANMessage
        return new CANMessage(id, data);
    }

    /**
     * Convert our internal CANMessage to a JavaCAN CanFrame.
     */
    private CanFrame convertToCanFrame(CANMessage message) {
        // JavaCAN has separate methods for standard and extended frames
        int rawId = message.getId().getRaw(); // 29-bit ID for extended, 11-bit for standard
        byte[] data = message.getData();
        boolean isExtended = message.getId().isExtended();
        
        try {
            CanFrame frame;
            if (isExtended) {
                // Use createExtended() for 29-bit extended IDs
                frame = CanFrame.createExtended(rawId, (byte)0, data);
            } else {
                // Use create() for 11-bit standard IDs
                frame = CanFrame.create(rawId, (byte)0, data);
            }
            
            return frame;
        } catch (Throwable e) {
            logger.error("Failed to create CanFrame for ID 0x{}: {}", 
                    String.format("%08X", rawId), e.getMessage(), e);
            throw new RuntimeException("Failed to create CanFrame", e);
        }
    }

    /**
     * Send a CAN message to the bus.
     *
     * @param message The message to send
     * @throws IOException if sending fails or not connected
     */
    @Override
    public void sendMessage(CANMessage message) throws IOException {
        RawCanChannel ch = this.channel;
        if (ch == null || !isConnected()) {
            throw new IOException("Not connected to SocketCAN interface");
        }

        CanFrame frame = convertToCanFrame(message);

        logger.info("📤 TX CAN: Sending CAN ID=0x{}, {} data bytes", 
                String.format("%08X", message.getId().getFullValue()),
                message.getData().length);

        try {
            synchronized (this) {
                logger.info("📤 TX CAN: About to call ch.write() on channel={}", ch != null ? "valid" : "null");
                ch.write(frame);
                logger.info("✅ TX CAN: ch.write() completed without exception");
            }
        } catch (IOException e) {
            // Write failed - connection is broken
            logger.debug("Send failed ({}), marking connection as down", e.getMessage());
            handleDisconnect();
            // Re-throw so caller knows the send failed
            throw e;
        }
    }

    /**
     * Check if the client is connected to the CAN interface.
     *
     * @return true if connected
     */
    @Override
    public boolean isConnected() {
        return connected && channel != null;
    }

    /**
     * Internal reconnection method used by reader thread.
     * Does NOT start a new reader thread (thread calls this on itself).
     */
    private synchronized void reconnectInternal() throws IOException {
        if (verboseLogging) {
            logger.debug("Internal reconnect: Opening SocketCAN interface...");
        }

        closeChannel();

        try {
            NetworkDevice device = NetworkDevice.lookup(interfaceName);
            RawCanChannel newChannel = CanChannels.newRawChannel(device);
            
            // CRITICAL: Bind the channel to enable TX/RX
            newChannel.bind(device);

            this.channel = newChannel;
            this.running = true;
            this.connected = true;

            // Restart periodic NETWORK broadcast after reconnection
            startKeepalive();

            logger.info("Reconnected to SocketCAN interface {}", interfaceName);
        } catch (Exception e) {
            throw new IOException(
                    "Failed to reconnect to SocketCAN interface " + interfaceName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Handle disconnect - now simplified since reader thread handles reconnection.
     * This is kept for explicit disconnect calls from sendMessage failures.
     */
    private synchronized void handleDisconnect() {
        if (!running && !connected) {
            return; // Already disconnected
        }

        if (verboseLogging) {
            logger.debug("Handling disconnect...");
        }

        // Stop keepalive when disconnected
        stopKeepalive();

        connected = false;
        running = false;
        closeChannel();

        // Reader thread will automatically reconnect
    }

    /**
     * Close the channel without attempting reconnection.
     */
    private void closeChannel() {
        try {
            RawCanChannel ch = this.channel;
            if (ch != null) {
                ch.close();
            }
        } catch (Exception e) {
            // Ignore
        }
        this.channel = null;
    }

    /**
     * Start periodic NETWORK broadcast to maintain presence on the IDS-CAN bus.
     */
    private void startKeepalive() {
        // Stop any existing keepalive task
        stopKeepalive();

        logger.debug("Starting NETWORK broadcast task (interval: {}ms)", KEEPALIVE_INTERVAL_MS);

        keepaliveTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (connected) {
                    sendKeepalive();
                }
            } catch (Exception e) {
                logger.warn("NETWORK broadcast task error: {}", e.getMessage(), e);
            }
        }, KEEPALIVE_INTERVAL_MS, KEEPALIVE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Stop the periodic NETWORK broadcast task.
     */
    private void stopKeepalive() {
        ScheduledFuture<?> task = keepaliveTask;
        if (task != null && !task.isDone()) {
            task.cancel(false);
            keepaliveTask = null;
            if (verboseLogging) {
                logger.debug("Keepalive stopped");
            }
        }
    }

    /**
     * Send a NETWORK broadcast to maintain presence on the IDS-CAN bus.
     */
    private void sendKeepalive() {
        try {
            // Create a proper IDS-CAN NETWORK broadcast message
            org.openhab.binding.idsmyrv.internal.idscan.MessageType msgType = org.openhab.binding.idsmyrv.internal.idscan.MessageType.NETWORK;
            org.openhab.binding.idsmyrv.internal.can.Address sourceAddr = new org.openhab.binding.idsmyrv.internal.can.Address(
                    this.sourceAddress);

            // Verify source address is set correctly
            if (this.sourceAddress < 0 || this.sourceAddress >= 256) {
                logger.warn("Invalid source address {} in keepalive, using 1", this.sourceAddress);
                sourceAddr = new org.openhab.binding.idsmyrv.internal.can.Address(1);
            }

            // NETWORK broadcast payload
            byte[] payload = new byte[] { 0x00, // NetworkStatus: 0 = normal
                    0x08, // ProtocolVersion: 8 (IDS-CAN v8)
                    0x00, 0x0A, 0x0B, 0x01, 0x00, 0x01 // Dummy MAC address
            };

            // Create proper IDS-CAN broadcast message and encode to CAN
            org.openhab.binding.idsmyrv.internal.idscan.IDSMessage idsMessage = org.openhab.binding.idsmyrv.internal.idscan.IDSMessage
                    .broadcast(msgType, sourceAddr, payload);
            CANMessage keepalive = idsMessage.encode();

            synchronized (this) {
                if (!connected || channel == null) {
                    return;
                }

                CanFrame frame = convertToCanFrame(keepalive);
                channel.write(frame);

                if (verboseLogging) {
                    logger.debug("NETWORK broadcast sent");
                }
            }
        } catch (IOException e) {
            logger.debug("NETWORK broadcast failed: {}", e.getMessage());
            handleDisconnect();
        } catch (Exception e) {
            logger.warn("NETWORK broadcast failed with unexpected error: {}", e.getMessage(), e);
        }
    }

    /**
     * Close the client and stop all operations.
     */
    @Override
    public void close() {
        shouldStop = true;
        running = false;
        connected = false;
        stopKeepalive();

        // Wait for reader thread to stop
        Thread thread = readerThread;
        if (thread != null && thread.isAlive()) {
            try {
                thread.interrupt();
                thread.join(1000); // Wait up to 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            readerThread = null;
        }

        closeChannel();

        logger.info("SocketCAN client closed");
    }
}

