# IDS-CAN Session Flow Analysis

## Response Code Enum (RESPONSE.cs)

```
0x00 = SUCCESS
0x01 = REQUEST_NOT_SUPPORTED
0x02 = BAD_REQUEST
0x03 = VALUE_OUT_OF_RANGE
0x04 = UNKNOWN_ID
0x05 = WRITE_VALUE_TOO_LARGE
0x06 = INVALID_ADDRESS
0x07 = READ_ONLY
0x08 = WRITE_ONLY
0x09 = CONDITIONS_NOT_CORRECT
0x0A = FEATURE_NOT_SUPPORTED
0x0B = BUSY
0x0C = SEED_NOT_REQUESTED
0x0D = KEY_NOT_CORRECT  ⚠️ THIS IS WHAT WE'RE GETTING!
0x0E = SESSION_NOT_OPEN
0x0F = TIMEOUT
0x10 = REMOTE_REQUEST_NOT_SUPPORTED
0x11 = IN_MOTION_LOCKOUT_ACTIVE
```

## Session Opening Flow

### Step 1: REQUEST_SEED (0x42 / 66)

**Client sends:**
- Message Type: REQUEST (0x80)
- Message Data: 0x42 (66)
- Data: `[SessionID: 2 bytes BE]` = `[00 04]` for session 4

**Device responds:**
- Message Type: RESPONSE (0x81)
- Message Data: 0x42 (66)
- Data: `[SessionID: 2 bytes BE][Seed: 4 bytes BE]` = `[00 04][Seed]`
- Response Code: SUCCESS (0x00) - embedded in message type

**C# SessionHost.cs (lines 139-166):**
- Validates message length == 2
- Validates SessionID matches
- Validates source address is valid device address
- If session already open → returns BUSY (0x0B)
- If safety lockout → returns CONDITIONS_NOT_CORRECT (0x09)
- Generates random seed
- Stores `AddressSeedWasSentTo = e.SourceAddress`
- Resets `SeedTimeout`
- Returns SUCCESS with `[SessionID][Seed]`

### Step 2: TRANSMIT_KEY (0x43 / 67)

**Client sends:**
- Message Type: REQUEST (0x80)
- Message Data: 0x43 (67)
- Data: `[SessionID: 2 bytes BE][Key: 4 bytes BE]` = `[00 04][EncryptedKey]`

**Device responds (SUCCESS):**
- Message Type: RESPONSE (0x81)
- Message Data: 0x43 (67)
- Data: `[SessionID: 2 bytes BE]` = `[00 04]`
- Response Code: SUCCESS (0x00)

**Device responds (ERROR):**
- Message Type: RESPONSE (0x81)
- Message Data: 0x43 (67)
- Data: `[ErrorCode: 1 byte]` = `[0x0D]` for KEY_NOT_CORRECT
- Response Code: Error code value

**C# SessionHost.cs (lines 167-209):**
- Validates message length == 6
- Validates SessionID matches
- **If `IsOpen == true` → returns CONDITIONS_NOT_CORRECT (0x09)** ⚠️
- Validates source address is valid device address
- **If `e.SourceAddress != AddressSeedWasSentTo` → returns SEED_NOT_REQUESTED (0x0C)** ⚠️
- Clears `AddressSeedWasSentTo = ADDRESS.INVALID`
- **If `SeedTimeout.Elapsed_sec > 3.5` → returns SEED_NOT_REQUESTED (0x0C)** ⚠️
- Extracts key: `uint uINT = e.Message.getUINT32(2)`
- Calculates expected key: `uint num = SessionID.Encrypt(Seed)`
- **If `uINT != num` → returns KEY_NOT_CORRECT (0x0D)** ⚠️ THIS IS OUR ERROR!
- If safety lockout → returns CONDITIONS_NOT_CORRECT (0x09)
- Sets `mClient.Address = e.SourceAddress`
- Resets timers
- Returns SUCCESS with `[SessionID]`

## Key Findings

### Error 0x0D = KEY_NOT_CORRECT

This means the encrypted key we're sending doesn't match what device 92 expects. The device calculates:
```
expectedKey = SessionID.Encrypt(Seed)
```

And compares it to the key we send. If they don't match, it returns `KEY_NOT_CORRECT`.

### Possible Causes

1. **Encryption algorithm mismatch** - Our Java implementation might have a bug
2. **Seed extraction error** - We might be reading the seed incorrectly
3. **Key transmission error** - We might be sending the key bytes in wrong order
4. **Timing issue** - Seed might expire before we send key (but we send immediately)
5. **Source address mismatch** - Device might expect key from different source (but we check this)

### Comparison: Reference vs Java

**Reference implementation (working):**
- Receives seed: `[00 04][Seed]`
- Extracts: `seed := binary.BigEndian.Uint32(msg.Data[2:6])`
- Encrypts: `key := sm.EncryptSeed(seed)`
- Sends: `[SessionID: 2 bytes BE][Key: 4 bytes BE]`
- Receives: `[00 04]` (success)

**Java client (failing):**
- Receives seed: `[00 04 F3 21 B0 84]` (example from logs)
- Extracts: `long seed = seedBuffer.getInt() & 0xFFFFFFFFL;` (bytes 2-5)
- Encrypts: `long key = encryptSeed(seed);`
- Sends: `[00 04][Key: 4 bytes BE]`
- Receives: `[0D]` (KEY_NOT_CORRECT error)

### Next Steps

1. **Verify encryption algorithm** - Compare reference and Java implementations byte-for-byte
2. **Test with known seed/key pairs** - Generate test cases for verification
3. **Check byte order** - Verify big-endian encoding/decoding
4. **Add detailed logging** - Log intermediate encryption steps
5. **Compare with C# implementation** - Check for any differences

## Encryption Algorithm

### Reference Implementation
```go
func (sm *SessionManager) EncryptSeed(seed uint32) uint32 {
    num := sm.cypher  // 2976579765 for remote control
    num2 := 32
    num3 := uint32(2654435769)

    for {
        seed += ((num << 4) + 1131376761) ^ (num + num3) ^ ((num >> 5) + 1919510376)
        num2--
        if num2 <= 0 {
            break
        }
        num += ((seed << 4) + 1948272964) ^ (seed + num3) ^ ((seed >> 5) + 1400073827)
        num3 += 2654435769
    }

    return seed
}
```

### Java Implementation (SessionManager.java:112-128)
```java
public long encryptSeed(long seed) {
    long num = CYPHER_REMOTE_CONTROL;  // 2976579765L
    int num2 = 32;
    long num3 = 2654435769L;

    while (true) {
        seed += ((num << 4) + 1131376761L) ^ (num + num3) ^ ((num >> 5) + 1919510376L);
        num2--;
        if (num2 <= 0) {
            break;
        }
        num += ((seed << 4) + 1948272964L) ^ (seed + num3) ^ ((seed >> 5) + 1400073827L);
        num3 += 2654435769L;
    }

    return seed & 0xFFFFFFFFL; // Ensure 32-bit
}
```

**These look identical!** So the issue must be elsewhere.

## Hypothesis

The encryption algorithm is correct, so the issue must be:
1. **Seed extraction** - Maybe we're reading wrong bytes?
2. **Key transmission** - Maybe bytes are in wrong order?
3. **Timing** - Maybe seed expires? (unlikely, we send immediately)
4. **Source address** - Maybe device expects different source? (but we check this)

Let's add detailed logging to trace the exact values at each step.

