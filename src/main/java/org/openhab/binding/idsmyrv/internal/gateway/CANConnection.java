package org.openhab.binding.idsmyrv.internal.gateway;

import java.io.IOException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;

/**
 * Interface for CAN bus connections.
 * 
 * Provides an abstraction layer that allows the bridge to communicate with the CAN bus
 * through different connection types:
 * - TCP Gateway (via GatewayClient)
 * - Direct SocketCAN (via SocketCANClient)
 * 
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public interface CANConnection {

    /**
     * Connect to the CAN bus.
     * 
     * @throws IOException if connection fails
     */
    void connect() throws IOException;

    /**
     * Send a CAN message to the bus.
     * 
     * @param message The message to send
     * @throws IOException if sending fails or not connected
     */
    void sendMessage(CANMessage message) throws IOException;

    /**
     * Check if the connection is established.
     * 
     * @return true if connected
     */
    boolean isConnected();

    /**
     * Close the connection and stop all operations.
     */
    void close();

    /**
     * Callback interface for handling received CAN messages.
     */
    @FunctionalInterface
    interface MessageHandler {
        /**
         * Handle a received CAN message.
         * 
         * @param message The received message
         */
        void handleMessage(CANMessage message);
    }
}

