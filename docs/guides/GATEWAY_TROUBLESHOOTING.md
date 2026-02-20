# Gateway Connection Troubleshooting

## Error Messages

```
Failed to handle command: Session opening timed out
Failed to send session message: Gateway not connected
```

This means the Bridge Handler can't reach your CAN gateway.

## Diagnostic Steps

### 1. Check Gateway Configuration

In OpenHAB UI:
- **Settings → Things → RV CAN Gateway**
- Verify **IP Address** is correct
- Verify **Port** is `8888`
- Check **Status** - should show the actual error

### 2. Test Network Connectivity from Container

```bash
# Get your container name
docker ps | grep openhab

# Test ping to gateway (replace with your gateway IP)
docker exec openhab ping -c 3 192.168.1.100

# Test TCP connection to gateway port
docker exec openhab nc -zv 192.168.1.100 8888

# Or use telnet
docker exec openhab telnet 192.168.1.100 8888
```

**Expected:**
- Ping should succeed
- Port 8888 should be open/accessible

### 3. Check Docker Network Mode

Your container might be in a different network mode:

```bash
# Check network mode
docker inspect openhab | grep -A 10 NetworkMode

# If it's "bridge" or custom, gateway IP needs to be accessible from container network
# If it's "host", gateway IP should be on your host's network
```

### 4. Enable Verbose Logging

**In OpenHAB UI:**
1. **Settings → Things → RV CAN Gateway → Edit**
2. **Show advanced** (click the toggle)
3. Set **Verbose** to `ON`
4. **Save**

**Check logs:**
```bash
docker logs -f openhab 2>&1 | grep -i "gateway\|idsmyrv"
```

**Look for:**
```
[INFO] Connecting to CAN gateway at 192.168.1.100:8888
[INFO] Successfully connected to CAN gateway
```

Or errors like:
```
[WARN] Failed to connect to CAN gateway: Connection refused
[WARN] Failed to connect to CAN gateway: Network is unreachable
[WARN] Failed to connect to CAN gateway: Connection timed out
```

### 5. Check Gateway is Running

On the host where your CAN gateway runs:

```bash
# Check if gateway is listening
netstat -an | grep 8888

# Or
lsof -i :8888

# Test from host machine
telnet 192.168.1.100 8888
```

## Common Issues & Solutions

### Issue 1: Gateway IP Changed
**Solution:** Update the IP address in Thing configuration

### Issue 2: Container Can't Reach Host Network
If your gateway is on your host machine (not in a container):

**macOS Docker Desktop:**
```
# Use special hostname instead of IP
IP Address: host.docker.internal
Port: 8888
```

**Linux:**
```
# Use host IP on docker bridge
# Find it with: ip addr show docker0
IP Address: 172.17.0.1  (or your docker0 IP)
Port: 8888
```

**Or use host network mode:**
```bash
docker run --network host ... openhab
```

### Issue 3: Firewall Blocking
```bash
# Check if firewall is blocking (Linux)
sudo iptables -L | grep 8888

# macOS
sudo pfctl -s rules | grep 8888

# Temporarily allow (Linux)
sudo iptables -A INPUT -p tcp --dport 8888 -j ACCEPT
```

### Issue 4: Gateway Not Started
```bash
# Start your CAN gateway using whatever software/hardware you have
# Ensure it is listening on 0.0.0.0:8888 (not just localhost)
```

## Quick Network Test

Run this from your **host machine**:

```bash
# Test if gateway is accessible
telnet 192.168.1.100 8888

# Or
nc -zv 192.168.1.100 8888

# If successful, you should connect
# Then test from container
docker exec openhab nc -zv 192.168.1.100 8888
```

If host works but container doesn't, it's a Docker networking issue.

## Configuration Examples

### Example 1: Gateway on Same Host as OpenHAB Container (macOS)
```
Bridge idsmyrv:gateway:mygateway "RV CAN Gateway" [
    ipAddress="host.docker.internal",
    port=8888,
    sourceAddress=1
]
```

### Example 2: Gateway on Different Device (Network)
```
Bridge idsmyrv:gateway:mygateway "RV CAN Gateway" [
    ipAddress="192.168.1.100",
    port=8888,
    sourceAddress=1
]
```

### Example 3: Gateway in Another Container
```
Bridge idsmyrv:gateway:mygateway "RV CAN Gateway" [
    ipAddress="gateway",    ← Container name (if on same network)
    port=8888,
    sourceAddress=1
]
```

## Verify Gateway is Accessible

```bash
# From host, test that the gateway accepts connections
telnet 192.168.1.100 8888
# Or: nc -zv 192.168.1.100 8888

# If host can connect but container cannot, it's a Docker networking issue
```

## Enable Debug in OpenHAB Console

```bash
ssh -p 8101 openhab@localhost

# Enable debug logging
log:set DEBUG org.openhab.binding.idsmyrv

# Watch logs
log:tail

# Try toggling the light again and watch the output
```

## Expected Successful Logs

```
[DEBUG] Initializing IDS MyRV Bridge
[INFO] Connecting to CAN gateway at 192.168.1.100:8888
[DEBUG] Gateway client connecting...
[INFO] Successfully connected to CAN gateway
[DEBUG] Bridge status updated to ONLINE
[DEBUG] Initializing Light Handler for thing idsmyrv:light:...
[DEBUG] Light Handler initialized successfully
[DEBUG] Received command: ON
[DEBUG] Requesting seed from device 92...
[DEBUG] Received seed response
[DEBUG] Transmitting session key...
[DEBUG] Session opened with device 92
[DEBUG] Sent ON command to light 92
[DEBUG] Received STATUS message from device 92
[DEBUG] Light 92 state updated: ON
```

---

**Most Likely Issue:** Gateway IP needs to be accessible from the container. Try `host.docker.internal` (macOS) or the actual network IP, and make sure the gateway is running and listening on 0.0.0.0:8888 (not just localhost).

