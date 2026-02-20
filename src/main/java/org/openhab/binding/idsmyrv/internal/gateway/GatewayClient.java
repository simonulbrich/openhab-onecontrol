package org.openhab.binding.idsmyrv.internal.gateway;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;
import org.openhab.binding.idsmyrv.internal.protocol.COBSDecoder;
import org.openhab.binding.idsmyrv.internal.protocol.COBSEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for connecting to the CAN gateway over TCP.
 *
 * Features:
 * - TCP connection with configurable timeout
 * - COBS protocol encoding/decoding
 * - Automatic reconnection on disconnect
 * - Thread-safe message sending
 * - Callback-based message handling
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class GatewayClient implements CANConnection {
    private final Logger logger = LoggerFactory.getLogger(GatewayClient.class);

    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_BUFFER_SIZE = 4096;
    private static final int RECONNECT_DELAY_MS = 2000;
    private static final int KEEPALIVE_INTERVAL_MS = 1000; // Send NETWORK broadcast every 1 second (like C#
                                                           // LocalDevice)

    private final String address;
    private final int port;
    private final CANConnection.MessageHandler messageHandler;
    private final ScheduledExecutorService scheduler;
    private final boolean verboseLogging;
    private final int sourceAddress;

    private @Nullable Socket socket;
    private @Nullable InputStream inputStream;
    private @Nullable OutputStream outputStream;
    private @Nullable Thread readerThread;
    private @Nullable ScheduledFuture<?> keepaliveTask;

    private final COBSEncoder encoder = new COBSEncoder();
    private final COBSDecoder decoder = new COBSDecoder();

    private volatile boolean running = false;
    private volatile boolean connected = false;
    private volatile boolean shouldStop = false;
    private volatile long lastTxTime = 0; // Track last transmission time

    /**
     * Create a new gateway client.
     *
     * @param address The gateway IP address
     * @param port The gateway port
     * @param messageHandler Callback for received messages
     * @param scheduler Executor for async operations
     * @param verboseLogging Enable verbose logging
     * @param sourceAddress The source address to use for NETWORK broadcasts (1-254)
     */
    public GatewayClient(String address, int port, CANConnection.MessageHandler messageHandler,
            ScheduledExecutorService scheduler, boolean verboseLogging, int sourceAddress) {
        this.address = address;
        this.port = port;
        this.messageHandler = messageHandler;
        this.scheduler = scheduler;
        this.verboseLogging = verboseLogging;
        this.sourceAddress = sourceAddress;
    }

    /**
     * Connect to the gateway.
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
            logger.debug("Connecting to CAN gateway at {}:{}...", address, port);
        }

        Socket newSocket = new Socket();
        newSocket.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
        newSocket.setKeepAlive(true); // Java 8 compatible - enables TCP keepalive
        newSocket.setTcpNoDelay(true); // Disable Nagle's algorithm for lower latency
        newSocket.setSoTimeout(1000); // 1 second read timeout (matches Go implementation)

        // Note: TCP keepalive interval configuration (TCP_KEEPIDLE, TCP_KEEPINTERVAL)
        // is not available in Java 8. We rely on application-level keepalive messages
        // (sent every 3 seconds) to prevent the gateway's idle timeout.

        this.socket = newSocket;
        this.inputStream = newSocket.getInputStream();
        this.outputStream = newSocket.getOutputStream();
        this.running = true;
        this.connected = true;
        this.shouldStop = false;
        this.lastTxTime = System.currentTimeMillis(); // Initialize to prevent immediate keepalive

        // Only start reader thread if not already started
        if (readerThread == null || !readerThread.isAlive()) {
            startReaderThread();
        }

        // Start periodic NETWORK broadcast to maintain presence on the bus
        // The gateway expects clients to be active participants, not just passive listeners.
        // We send a proper IDS-CAN NETWORK broadcast every 1 second (like C# LocalDevice).
        startKeepalive();

        logger.info("Connected to CAN gateway at {}:{}", address, port);
    }

    /**
     * Start the reader thread that processes incoming data.
     * Uses a single continuous thread like the Go implementation.
     * Handles reconnection internally like the Go readLoop.
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
            byte[] buffer = new byte[READ_BUFFER_SIZE];

            while (!shouldStop) {
                // Check if connected, if not try to reconnect (like Go readLoop lines 236-247)
                if (!connected || inputStream == null) {
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
                    InputStream input = this.inputStream;
                    if (input == null) {
                        continue;
                    }

                    // Try to read with timeout (like Go conn.Read with deadline)
                    int bytesRead;
                    try {
                        bytesRead = input.read(buffer);
                    } catch (java.net.SocketTimeoutException e) {
                        // Timeout is normal - no data available, just continue (like Go line 256-257)
                        continue;
                    }

                    if (bytesRead > 0) {
                        // Log raw bytes received (DEBUG only - too verbose for INFO)
                        logger.debug("📥 Raw TCP RX ({} bytes): {}", bytesRead,
                                Arrays.toString(Arrays.copyOf(buffer, Math.min(bytesRead, 64))));

                        // Process received data
                        byte[] data = java.util.Arrays.copyOf(buffer, bytesRead);
                        List<byte[]> messages = decoder.decodeBytes(data);

                        if (!messages.isEmpty()) {
                            logger.debug("📦 COBS decoded {} message(s)", messages.size());
                        }

                        for (byte[] msgData : messages) {
                            // Skip empty messages (they're not real CAN messages)
                            if (msgData.length == 0) {
                                logger.trace("Ignoring empty message from COBS decoder");
                                continue;
                            }

                            try {
                                CANMessage msg = CANMessage.unmarshal(msgData);
                                logger.debug("RX CAN: {}", msg);
                                messageHandler.handleMessage(msg);
                            } catch (IllegalArgumentException e) {
                                // Check if it's the "must be at least 1 byte" error for empty messages
                                if (msgData.length == 0 || e.getMessage().contains("at least 1 byte")) {
                                    logger.trace("Ignoring empty or invalid message: {} - data: {}", e.getMessage(),
                                            Arrays.toString(msgData));
                                } else {
                                    logger.debug("Failed to unmarshal message: {} - data: {}", e.getMessage(),
                                            Arrays.toString(msgData));
                                }
                            } catch (Exception e) {
                                logger.debug("Failed to unmarshal message: {} - data: {}", e.getMessage(),
                                        Arrays.toString(msgData));
                            }
                        }
                    } else if (bytesRead == -1) {
                        // EOF - connection closed (like Go line 259-260)
                        logger.debug("Gateway closed connection (EOF)");
                        synchronized (this) {
                            connected = false;
                            running = false;
                            closeSocket();
                        }
                        // Don't break - loop will try to reconnect
                        continue;
                    }
                } catch (IOException e) {
                    // Read error (like Go line 262)
                    logger.warn("❌ Socket read error: {} - disconnecting", e.getMessage());
                    synchronized (this) {
                        connected = false;
                        running = false;
                        closeSocket();
                    }
                    // Don't break - loop will try to reconnect
                    continue;
                } catch (Exception e) {
                    logger.warn("Unexpected error in reader: {}", e.getMessage());
                }
            }

            if (verboseLogging) {
                logger.debug("Reader thread stopped");
            }
        }, "GatewayClient-Reader");

        readerThread.setDaemon(true);
        readerThread.start();
    }

    // Removed readMessages() - now handled directly in reader thread

    /**
     * Send a CAN message to the gateway.
     *
     * @param message The message to send
     * @throws IOException if sending fails or not connected
     */
    @Override
    public void sendMessage(CANMessage message) throws IOException {
        OutputStream output = this.outputStream;
        if (output == null || !isConnected()) {
            throw new IOException("Not connected to gateway");
        }

        byte[] data = message.marshal();
        byte[] encoded = encoder.encode(data);

        logger.debug("Sending to gateway: CAN ID=0x{}, {} data bytes, {} encoded bytes",
                String.format("%08X", message.getId().getFullValue()), data.length, encoded.length);

        try {
            synchronized (this) {
                output.write(encoded);
                output.flush();
                lastTxTime = System.currentTimeMillis(); // Track last transmission for keepalive
            }

            if (verboseLogging) {
                logger.debug("Sent: {}", message);
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
     * Check if the client is connected to the gateway.
     *
     * @return true if connected
     */
    @Override
    public boolean isConnected() {
        Socket s = this.socket;
        return connected && s != null && s.isConnected() && !s.isClosed();
    }

    /**
     * Internal reconnection method used by reader thread.
     * Does NOT start a new reader thread (thread calls this on itself).
     */
    private synchronized void reconnectInternal() throws IOException {
        if (verboseLogging) {
            logger.debug("Internal reconnect: Establishing new socket connection...");
        }

        closeSocket();

        Socket newSocket = new Socket();
        newSocket.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
        newSocket.setKeepAlive(true);
        newSocket.setTcpNoDelay(true);
        newSocket.setSoTimeout(1000);

        this.socket = newSocket;
        this.inputStream = newSocket.getInputStream();
        this.outputStream = newSocket.getOutputStream();
        this.running = true;
        this.connected = true;
        this.lastTxTime = System.currentTimeMillis(); // Reset transmission time

        // Restart periodic NETWORK broadcast after reconnection
        startKeepalive();

        logger.info("Reconnected to CAN gateway at {}:{}", address, port);
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
        closeSocket();

        // Reader thread will automatically reconnect
    }

    /**
     * Close the socket without attempting reconnection.
     */
    private void closeSocket() {
        try {
            Socket s = this.socket;
            if (s != null && !s.isClosed()) {
                s.close();
            }
        } catch (IOException e) {
            // Ignore
        }
        this.socket = null;
        this.inputStream = null;
        this.outputStream = null;
    }

    /**
     * Start periodic NETWORK broadcast to maintain presence on the IDS-CAN bus.
     * The C# LocalDevice sends NETWORK broadcasts unconditionally every 1 second.
     * The gateway expects clients to be active participants, not just passive listeners.
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
     * We send a proper IDS-CAN NETWORK broadcast (MessageType 0).
     * This mimics the periodic messages sent by LocalDevice in the C# implementation.
     */
    private void sendKeepalive() {
        try {
            // Create a proper IDS-CAN NETWORK broadcast message
            // The C# LocalDevice sends: NETWORK (type=0), NetworkStatus, ProtocolVersion, MAC[6 bytes]
            org.openhab.binding.idsmyrv.internal.idscan.MessageType msgType = org.openhab.binding.idsmyrv.internal.idscan.MessageType.NETWORK;
            org.openhab.binding.idsmyrv.internal.can.Address sourceAddr = new org.openhab.binding.idsmyrv.internal.can.Address(
                    this.sourceAddress); // Use configured source address

            // Verify source address is set correctly
            if (this.sourceAddress < 0 || this.sourceAddress >= 256) {
                logger.warn("Invalid source address {} in keepalive, using 1", this.sourceAddress);
                sourceAddr = new org.openhab.binding.idsmyrv.internal.can.Address(1);
            }

            // NETWORK broadcast payload:
            // Byte 0: NetworkStatus (0x00 = normal)
            // Byte 1: ProtocolVersion (0x08 = IDS-CAN v8, most common version)
            // Bytes 2-7: MAC address (use a dummy MAC for now)
            byte[] payload = new byte[] { 0x00, // NetworkStatus: 0 = normal
                    0x08, // ProtocolVersion: 8 (IDS-CAN v8)
                    0x00, 0x0A, 0x0B, 0x01, 0x00, 0x01 // Dummy MAC address
            };

            // Create proper IDS-CAN broadcast message and encode to CAN
            org.openhab.binding.idsmyrv.internal.idscan.IDSMessage idsMessage = org.openhab.binding.idsmyrv.internal.idscan.IDSMessage
                    .broadcast(msgType, sourceAddr, payload);
            CANMessage keepalive = idsMessage.encode();

            synchronized (this) {
                if (!connected || outputStream == null) {
                    return;
                }

                byte[] data = keepalive.marshal();
                byte[] encoded = encoder.encode(data);

                if (verboseLogging) {
                    logger.debug("NETWORK broadcast: data={} bytes, encoded={} bytes", data.length, encoded.length);
                    logger.trace("   Raw data: {}", formatBytes(data));
                    logger.trace("   COBS encoded: {}", formatBytes(encoded));
                }

                outputStream.write(encoded);
                outputStream.flush();
                lastTxTime = System.currentTimeMillis();
            }
        } catch (IOException e) {
            logger.debug("NETWORK broadcast failed: {}", e.getMessage());
            handleDisconnect();
        } catch (Exception e) {
            logger.warn("NETWORK broadcast failed with unexpected error: {}", e.getMessage(), e);
        }
    }

    /**
     * Format bytes for logging.
     */
    private String formatBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0)
                sb.append(" ");
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Close the gateway client and stop all operations.
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

        closeSocket();
        decoder.reset();

        logger.info("Gateway client closed");
    }
}
