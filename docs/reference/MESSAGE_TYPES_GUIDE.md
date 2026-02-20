# IDS-CAN Message Types Guide

## Overview

The IDS-CAN bus has two categories of messages:
- **Broadcast Messages** (standard 11-bit CAN ID) - sent to all devices
- **Point-to-Point Messages** (extended 29-bit CAN ID) - sent between specific devices

---

## Broadcast Messages (Informational - Can Usually Suppress)

### 🌐 NETWORK (0)
- **Purpose**: Network-level status and coordination
- **Example**: `[03 14 00 00 00 0C 06 A4]`
- **Frequency**: Periodic (every few seconds)
- **Action**: Usually just heartbeats - **can suppress**

### 🔌 CIRCUIT_ID (1)
- **Purpose**: Circuit identification broadcasts
- **Frequency**: Occasional
- **Action**: Identifies electrical circuits - **can suppress unless debugging**

### 🏷️ DEVICE_ID (2)
- **Purpose**: Device announces its presence and identity
- **Example**: From a light, pump, etc.
- **Frequency**: On startup or when requested
- **Action**: Useful for discovery - **can suppress in normal operation**

### 📊 DEVICE_STATUS (3)
- **Purpose**: Device broadcasts its current state
- **Example**: Light brightness, tank level, battery voltage
- **Example Data**: `[00 21 AD F4 02]` from address 152 (your light!)
- **Frequency**: When state changes or periodically
- **Action**: **KEEP** - this tells you when devices change state

### 📦 PRODUCT_STATUS (6)
- **Purpose**: Product-level status information
- **Frequency**: Occasional
- **Action**: **Can suppress** unless debugging

### ⏰ TIME (7)
- **Purpose**: Time synchronization broadcasts
- **Frequency**: Periodic
- **Action**: **Can suppress** - not needed for basic operation

---

## Point-to-Point Messages (Critical for Device Control)

### 📤 REQUEST (128)
- **Purpose**: Ask another device to do something or provide information
- **Examples**:
  - REQUEST_SEED (0x42): "Give me a random seed to start a session"
  - REQUEST_STATUS (0x10): "Tell me your current status"
- **Frequency**: When you need info from a device
- **Action**: **KEEP** - needed to see what we're asking devices

### 📥 RESPONSE (129)
- **Purpose**: Reply to a REQUEST
- **Examples**:
  - RESPONSE_SEED (0x43): "Here's the seed: [00 11 22 33 44 55]"
  - RESPONSE_CHALLENGE (0x45): "Session authenticated"
- **Frequency**: After each REQUEST
- **Action**: **KEEP** - essential for session management

### ⚡ COMMAND (130)
- **Purpose**: Tell another device to do something (no reply expected)
- **Examples**:
  - TURN_ON (0x21): "Turn on"
  - TURN_OFF (0x20): "Turn off"
  - SET_BRIGHTNESS (0x22): "Set to 50%"
- **Frequency**: When you control a device
- **Action**: **KEEP** - shows what commands we're sending

### 📈 EXT_STATUS (131)
- **Purpose**: Extended status messages with more detail
- **Frequency**: Occasional
- **Action**: **Can suppress** unless debugging

### 📝 TEXT_CONSOLE (132)
- **Purpose**: Debug text messages (often spaces or system messages)
- **Example**: `[20 20 20 20 20 20 20 20]` (8 spaces)
- **Frequency**: Very frequent
- **Action**: **SUPPRESS** - mostly noise, just debug info from devices

---

## Recommended Logging Levels

### ✅ KEEP at INFO level:
- **DEVICE_STATUS** broadcasts (to see state changes)
- **REQUEST** messages (to see what we're asking)
- **RESPONSE** messages (to see replies)
- **COMMAND** messages (to see control actions)
- Gateway connection status
- Session open/close

### ⚠️ Keep at DEBUG level:
- **NETWORK** broadcasts
- **DEVICE_ID** broadcasts
- **CIRCUIT_ID** broadcasts
- **EXT_STATUS** messages
- COBS decoding details
- Raw TCP data

### 🔇 SUPPRESS or TRACE level:
- **TEXT_CONSOLE** messages (almost always noise)
- **PRODUCT_STATUS** broadcasts
- **TIME** broadcasts
- Detailed session keepalive messages

---

## Example: Light Switch Sequence

When you toggle a light, here's what you should see:

```
1. [INFO] Session not open, establishing session with device 92
2. [INFO] 📤 Sending REQUEST(128): SEED_REQUEST to device 92
3. [INFO] 📥 Received RESPONSE(129): SEED from device 92 -> [00 11 22 33 44 55]
4. [INFO] 📤 Sending REQUEST(128): CHALLENGE to device 92 -> [calculated response]
5. [INFO] 📥 Received RESPONSE(129): CHALLENGE_ACK from device 92
6. [INFO] Session opened successfully
7. [INFO] 📤 Sending COMMAND(130): TURN_ON to device 92
8. [INFO] 📥 Received DEVICE_STATUS(3) from device 92: [new state]
```

Everything else (NETWORK, TEXT_CONSOLE, etc.) is just background noise.

