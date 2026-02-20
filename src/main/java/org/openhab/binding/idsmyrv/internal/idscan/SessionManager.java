package org.openhab.binding.idsmyrv.internal.idscan;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages IDS-CAN sessions for device control.
 *
 * The IDS-CAN protocol requires a REMOTE_CONTROL session (ID 4) to be established
 * before sending commands to a device. This involves a seed-challenge-response
 * authentication process.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class SessionManager {
    private final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    // Session constants
    private static final int SESSION_ID_REMOTE_CONTROL = 4;
    private static final long CYPHER_REMOTE_CONTROL = 2976579765L;

    // Request types
    private static final int REQUEST_SESSION_REQUEST_SEED = 66;
    private static final int REQUEST_SESSION_TRANSMIT_KEY = 67;
    private static final int REQUEST_SESSION_HEARTBEAT = 68;
    private static final int REQUEST_SESSION_END = 69;

    // Timing constants
    private static final long HEARTBEAT_INTERVAL_MS = 4000;
    private static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofSeconds(30);

    private final Address sourceAddress;
    private final Address targetAddress;
    private final MessageSender messageSender;
    private final ScheduledExecutorService scheduler;

    private volatile boolean isOpen = false;
    private Instant lastActivity = Instant.now();
    private Duration idleTimeout = DEFAULT_IDLE_TIMEOUT;

    private @Nullable ScheduledFuture<?> heartbeatTask;

    /**
     * Create a new session manager.
     *
     * @param sourceAddress The source address (gateway/controller)
     * @param targetAddress The target device address
     * @param messageSender Callback for sending CAN messages
     * @param scheduler Executor for heartbeat task
     */
    public SessionManager(Address sourceAddress, Address targetAddress, MessageSender messageSender,
            ScheduledExecutorService scheduler) {
        this.sourceAddress = sourceAddress;
        this.targetAddress = targetAddress;
        this.messageSender = messageSender;
        this.scheduler = scheduler;
    }

    /**
     * Check if the session is currently open.
     * Also checks for idle timeout.
     *
     * @return true if session is open and not expired
     */
    public synchronized boolean isOpen() {
        if (!isOpen) {
            return false;
        }

        // Check idle timeout
        if (Duration.between(lastActivity, Instant.now()).compareTo(idleTimeout) > 0) {
            logger.debug("Session with device {} expired after {} of inactivity", targetAddress.getValue(),
                    idleTimeout);
            isOpen = false;
            stopHeartbeat();
            return false;
        }

        return true;
    }

    /**
     * Get the target device address.
     *
     * @return The target address
     */
    public Address getTargetAddress() {
        return targetAddress;
    }

    /**
     * Encrypt a seed value using the TEA-like encryption algorithm.
     * This matches the C# implementation.
     *
     * @param seed The seed value to encrypt
     * @return The encrypted key
     */
    public long encryptSeed(long seed) {
        // Use unsigned 32-bit arithmetic to match C#/Go
        // All values must be masked to 32 bits after each operation
        long num = CYPHER_REMOTE_CONTROL & 0xFFFFFFFFL;
        int num2 = 32;
        long num3 = 2654435769L & 0xFFFFFFFFL;
        seed = seed & 0xFFFFFFFFL; // Ensure seed is 32-bit

        while (true) {
            // C#: seed += ((num << 4) + 1131376761) ^ (num + num3) ^ ((num >> 5) + 1919510376);
            // Use >>> for unsigned right shift (Java >> is signed)
            long term1 = ((num << 4) + 1131376761L) & 0xFFFFFFFFL;
            long term2 = (num + num3) & 0xFFFFFFFFL;
            long term3 = ((num >>> 5) + 1919510376L) & 0xFFFFFFFFL; // >>> is unsigned right shift
            seed = (seed + (term1 ^ term2 ^ term3)) & 0xFFFFFFFFL;

            num2--;
            if (num2 <= 0) {
                break;
            }

            // C#: num += ((seed << 4) + 1948272964) ^ (seed + num3) ^ ((seed >> 5) + 1400073827);
            long term4 = ((seed << 4) + 1948272964L) & 0xFFFFFFFFL;
            long term5 = (seed + num3) & 0xFFFFFFFFL;
            long term6 = ((seed >>> 5) + 1400073827L) & 0xFFFFFFFFL; // >>> is unsigned right shift
            num = (num + (term4 ^ term5 ^ term6)) & 0xFFFFFFFFL;

            num3 = (num3 + 2654435769L) & 0xFFFFFFFFL;
        }

        return seed & 0xFFFFFFFFL; // Ensure 32-bit result
    }

    /**
     * Request a seed from the device to start session opening.
     * This is asynchronous - the response will be processed by processResponse().
     *
     * @throws Exception if sending fails
     */
    public void requestSeed() throws Exception {
        logger.debug("Requesting seed from device {}...", targetAddress.getValue());

        // Build REQUEST message with SESSION_REQUEST_SEED (66)
        // Data: [SessionID: 2 bytes BE]
        ByteBuffer buffer = ByteBuffer.allocate(2);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) SESSION_ID_REMOTE_CONTROL);

        IDSMessage msg = IDSMessage.pointToPoint(MessageType.REQUEST, sourceAddress, targetAddress,
                REQUEST_SESSION_REQUEST_SEED, buffer.array());

        CANMessage canMsg = msg.encode();
        messageSender.sendMessage(canMsg);
    }

    private String getErrorName(int errorCode) {
        switch (errorCode) {
            case 0:
                return "SUCCESS";
            case 1:
                return "REQUEST_NOT_SUPPORTED";
            case 2:
                return "BAD_REQUEST";
            case 3:
                return "VALUE_OUT_OF_RANGE";
            case 4:
                return "UNKNOWN_ID";
            case 5:
                return "WRITE_VALUE_TOO_LARGE";
            case 6:
                return "INVALID_ADDRESS";
            case 7:
                return "READ_ONLY";
            case 8:
                return "WRITE_ONLY";
            case 9:
                return "CONDITIONS_NOT_CORRECT";
            case 10:
                return "FEATURE_NOT_SUPPORTED";
            case 11:
                return "BUSY";
            case 12:
                return "SEED_NOT_REQUESTED";
            case 13:
                return "KEY_NOT_CORRECT"; // 0x0D - encrypted key doesn't match
            case 14:
                return "SESSION_NOT_OPEN";
            case 15:
                return "TIMEOUT";
            case 16:
                return "REMOTE_REQUEST_NOT_SUPPORTED";
            case 17:
                return "IN_MOTION_LOCKOUT_ACTIVE";
            default:
                return "UNKNOWN_ERROR";
        }
    }

    /**
     * Transmit an encrypted key to complete session opening.
     *
     * @param key The encrypted key
     * @throws Exception if sending fails
     */
    private void transmitKey(long key) throws Exception {
        logger.debug("Transmitting key to device {}...", targetAddress.getValue());

        // Build REQUEST message with SESSION_TRANSMIT_KEY (67)
        // Data: [SessionID: 2 bytes BE][Key: 4 bytes BE]
        ByteBuffer buffer = ByteBuffer.allocate(6);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) SESSION_ID_REMOTE_CONTROL);
        buffer.putInt((int) key);

        IDSMessage msg = IDSMessage.pointToPoint(MessageType.REQUEST, sourceAddress, targetAddress,
                REQUEST_SESSION_TRANSMIT_KEY, buffer.array());

        CANMessage canMsg = msg.encode();
        messageSender.sendMessage(canMsg);
    }

    /**
     * Send a heartbeat to keep the session alive.
     *
     * @throws Exception if sending fails
     */
    public synchronized void sendHeartbeat() throws Exception {
        if (!isOpen) {
            throw new IllegalStateException("Session not open");
        }

        // Build REQUEST message with SESSION_HEARTBEAT (68)
        // Data: [SessionID: 2 bytes BE]
        ByteBuffer buffer = ByteBuffer.allocate(2);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) SESSION_ID_REMOTE_CONTROL);

        IDSMessage msg = IDSMessage.pointToPoint(MessageType.REQUEST, sourceAddress, targetAddress,
                REQUEST_SESSION_HEARTBEAT, buffer.array());

        CANMessage canMsg = msg.encode();
        messageSender.sendMessage(canMsg);

        lastActivity = Instant.now();
        logger.debug("Heartbeat sent to device {}", targetAddress.getValue());
    }

    /**
     * Close the session.
     * Only sends the close request if the session is currently open.
     * This prevents unnecessary messages when the session is already closed.
     *
     * @throws Exception if sending fails
     */
    public synchronized void closeSession() throws Exception {
        if (!isOpen) {
            // Session already closed, nothing to do
            logger.debug("Session with device {} is already closed", targetAddress.getValue());
            return;
        }

        logger.debug("Closing session with device {}...", targetAddress.getValue());

        // Build REQUEST message with SESSION_END (69)
        // Data: [SessionID: 2 bytes BE]
        ByteBuffer buffer = ByteBuffer.allocate(2);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) SESSION_ID_REMOTE_CONTROL);

        IDSMessage msg = IDSMessage.pointToPoint(MessageType.REQUEST, sourceAddress, targetAddress, REQUEST_SESSION_END,
                buffer.array());

        CANMessage canMsg = msg.encode();
        messageSender.sendMessage(canMsg);

        isOpen = false;
        stopHeartbeat();
    }

    /**
     * Process a response message from the device.
     * This handles seed responses, key confirmations, heartbeat responses, etc.
     *
     * @param msg The IDS message
     */
    public synchronized void processResponse(IDSMessage msg) {
        if (msg.getMessageType() != MessageType.RESPONSE) {
            return;
        }

        // Check if this response is addressed to us
        if (!msg.getTargetAddress().equals(sourceAddress)) {
            logger.debug("Ignoring response not addressed to us (target={}, expected={})",
                    msg.getTargetAddress().getValue(), sourceAddress.getValue());
            return;
        }

        // NOTE: Unlike C# code, Go code does NOT check if response is from our target device.
        // This is correct - responses may come from a different device (e.g., a gateway/controller).
        // We only check that it's addressed to us and has the right messageData.

        byte[] data = msg.getData();
        int messageData = msg.getMessageData();

        logger.debug("Received RESPONSE: messageData=0x{}, dataLength={}, from device {} to device {}",
                String.format("%02X", messageData), data.length, msg.getSourceAddress().getValue(),
                msg.getTargetAddress().getValue());

        try {
            switch (messageData) {
                case REQUEST_SESSION_REQUEST_SEED:
                    // Response to seed request
                    // Data: [SessionID: 2 bytes][Seed: 4 bytes]
                    if (data.length < 6) {
                        logger.warn("Invalid seed response length: {}", data.length);
                        return;
                    }

                    ByteBuffer seedBuffer = ByteBuffer.wrap(data);
                    seedBuffer.order(ByteOrder.BIG_ENDIAN);
                    int sessionID = seedBuffer.getShort() & 0xFFFF;
                    long seed = seedBuffer.getInt() & 0xFFFFFFFFL;

                    logger.debug("Received seed: 0x{}, sessionID={}", Long.toHexString(seed).toUpperCase(), sessionID);

                    // Encrypt and send key
                    long key = encryptSeed(seed);
                    logger.debug("Encrypted key: 0x{}", Long.toHexString(key).toUpperCase());

                    transmitKey(key);
                    break;

                case REQUEST_SESSION_TRANSMIT_KEY:
                    // Response to key transmission
                    // Valid response should be 2 bytes: [SessionID: 2 bytes BE]
                    // Single-byte responses are error codes
                    if (msg.getData().length == 2) {
                        // Verify session ID matches
                        ByteBuffer respBuffer = ByteBuffer.wrap(msg.getData());
                        respBuffer.order(ByteOrder.BIG_ENDIAN);
                        int respSessionID = respBuffer.getShort() & 0xFFFF;

                        if (respSessionID == SESSION_ID_REMOTE_CONTROL) {
                            // Valid response - session is now open
                            isOpen = true;
                            lastActivity = Instant.now();
                            startHeartbeat();
                            logger.info("Session opened with device {}", targetAddress.getValue());
                        } else {
                            logger.warn("TRANSMIT_KEY response has wrong session ID: expected {}, got {}",
                                    SESSION_ID_REMOTE_CONTROL, respSessionID);
                        }
                    } else if (msg.getData().length == 1) {
                        // Error code response
                        byte errorCode = msg.getData()[0];
                        String errorName = getErrorName(errorCode);
                        logger.error("TRANSMIT_KEY rejected by device {}: error 0x{} ({}) - {}",
                                targetAddress.getValue(), String.format("%02X", errorCode & 0xFF), errorCode & 0xFF,
                                errorName);
                        // Do NOT mark session as open
                    } else {
                        logger.warn("TRANSMIT_KEY response has unexpected length: {} bytes (expected 2)",
                                msg.getData().length);
                        // Do NOT mark session as open
                    }
                    break;

                case REQUEST_SESSION_HEARTBEAT:
                    // Response to heartbeat - check for error code
                    // From C# SessionHost.cs: if session not open OR source address doesn't match,
                    // device returns error code 0x0E (14 = SESSION_NOT_OPEN)
                    if (data.length >= 3) {
                        int errorCode = data[2] & 0xFF;
                        if (errorCode == 0x0E) {
                            // Session not recognized - close it immediately
                            isOpen = false;
                            stopHeartbeat();
                            logger.warn(
                                    "Heartbeat rejected: session not recognized by device {} (error 0x{} = SESSION_NOT_OPEN). Session closed.",
                                    targetAddress.getValue(), String.format("%02X", errorCode));
                        } else if (errorCode != 0) {
                            // Other error codes - also close session to be safe
                            isOpen = false;
                            stopHeartbeat();
                            logger.warn("Heartbeat rejected by device {} with error code 0x{} ({}). Session closed.",
                                    targetAddress.getValue(), String.format("%02X", errorCode),
                                    getErrorName(errorCode));
                        } else {
                            // Success - heartbeat acknowledged
                            lastActivity = Instant.now();
                            logger.debug("Heartbeat acknowledged by device {}", targetAddress.getValue());
                        }
                    } else if (data.length == 2) {
                        // Success response (no error code) - heartbeat acknowledged
                        lastActivity = Instant.now();
                        logger.debug("Heartbeat acknowledged by device {} (no error code)", targetAddress.getValue());
                    }
                    break;

                case REQUEST_SESSION_END:
                    // Response to session close
                    isOpen = false;
                    stopHeartbeat();
                    break;

                default:
                    // Not a session-related response
                    logger.debug("Non-session response: messageData=0x{:02X}", String.format("%02X", messageData));
                    break;
            }
        } catch (Exception e) {
            logger.error("Error processing session response: {}", e.getMessage());
        }
    }

    /**
     * Update activity timestamp.
     * Call this when sending commands to prevent idle timeout.
     */
    public synchronized void updateActivity() {
        lastActivity = Instant.now();
    }

    /**
     * Set the idle timeout duration.
     *
     * @param timeout The timeout duration
     */
    public synchronized void setIdleTimeout(Duration timeout) {
        this.idleTimeout = timeout;
    }

    /**
     * Start periodic heartbeat task.
     */
    private void startHeartbeat() {
        if (heartbeatTask != null) {
            return; // Already running
        }

        heartbeatTask = scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (!isOpen()) {
                    // Session closed - stop heartbeat
                    stopHeartbeat();
                    return;
                }
                sendHeartbeat();
            } catch (Exception e) {
                logger.debug("Heartbeat failed: {}", e.getMessage());
                // Let it retry next time
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        logger.debug("Heartbeat task started for device {}", targetAddress.getValue());
    }

    /**
     * Stop the heartbeat task.
     */
    private void stopHeartbeat() {
        ScheduledFuture<?> task = this.heartbeatTask;
        if (task != null) {
            task.cancel(false);
            this.heartbeatTask = null;
            logger.debug("Heartbeat task stopped for device {}", targetAddress.getValue());
        }
    }

    /**
     * Shutdown the session manager.
     */
    public void shutdown() {
        try {
            if (isOpen) {
                closeSession();
            }
        } catch (Exception e) {
            logger.debug("Error closing session during shutdown: {}", e.getMessage());
        }
        stopHeartbeat();
    }

    /**
     * Functional interface for sending CAN messages.
     */
    @FunctionalInterface
    public interface MessageSender {
        /**
         * Send a CAN message.
         *
         * @param message The message to send
         * @throws Exception if sending fails
         */
        void sendMessage(CANMessage message) throws Exception;
    }
}
