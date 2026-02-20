# Quick Start: Using SocketCAN with Your New CAN Adapter

## Prerequisites
- OpenHAB server running on Linux
- CAN adapter hardware installed (USB CAN adapter, etc.)
- Root/sudo access to configure CAN interface

## Step 1: Install the Updated Binding

1. Copy the new binding JAR to your OpenHAB addons folder:
   ```bash
   sudo cp target/org.openhab.binding.idsmyrv-5.0.2-SNAPSHOT.jar \
       /path/to/openhab/addons/
   ```

2. Restart OpenHAB:
   ```bash
   sudo systemctl restart openhab
   ```

## Step 2: Set Up Your CAN Interface

1. Load kernel modules:
   ```bash
   sudo modprobe can
   sudo modprobe can_raw
   ```

2. Configure your CAN interface (adjust bitrate to match your bus):
   ```bash
   # Most RV systems use 250000 bps
   sudo ip link set can0 type can bitrate 250000
   sudo ip link set can0 up
   ```

3. Verify the interface is up:
   ```bash
   ip -details link show can0
   ```

4. Make it persistent (create `/etc/network/interfaces.d/can0`):
   ```
   auto can0
   iface can0 can static
       bitrate 250000
       up ip link set $IFACE up
       down ip link set $IFACE down
   ```

## Step 3: Configure OpenHAB Bridge

### Option A: Via UI
1. Go to Settings → Things
2. Add Thing → IDS MyRV Binding → IDS MyRV Gateway
3. Configure:
   - **Connection Type**: Direct SocketCAN
   - **CAN Interface**: can0
   - **Source Address**: 1 (or unique value if needed)
   - **Verbose Logging**: false (enable for debugging)

### Option B: Via Configuration File
Create/edit `things/idsmyrv.things`:
```
Bridge idsmyrv:gateway:mybridge "RV CAN Bus" [
    connectionType="socketcan",
    canInterface="can0",
    sourceAddress=1,
    verbose=false
] {
    // Your existing device things here
}
```

## Step 4: Test the Connection

1. Check OpenHAB logs:
   ```bash
   tail -f /path/to/openhab/log/openhab.log
   ```

2. Look for:
   ```
   [INFO ] Connected to SocketCAN interface can0
   ```

3. Monitor CAN traffic:
   ```bash
   candump can0
   ```

## Troubleshooting

### Interface Not Found
```
Error: Failed to open SocketCAN interface can0
```
**Solution**: Check that interface exists: `ip link show can0`

### Permission Denied
```
Error: Operation not permitted
```
**Solution**: OpenHAB may need elevated permissions. Add user to dialout group:
```bash
sudo usermod -a -G dialout openhab
sudo systemctl restart openhab
```

### No CAN Traffic
```
Connected but no messages received
```
**Solution**: 
- Check physical connections
- Verify bitrate matches your bus
- Check CAN bus termination (120Ω resistors)

### Wrong Bitrate
```
Error frames or no communication
```
**Solution**: Try different bitrates:
```bash
sudo ip link set can0 down
sudo ip link set can0 type can bitrate 500000  # or 250000, 125000
sudo ip link set can0 up
```

## Reverting to TCP Gateway Mode

If you need to switch back:

1. Update Thing configuration:
   ```
   connectionType="tcp",
   ipAddress="192.168.1.100",
   port=8888
   ```

2. Restart OpenHAB

## Common CAN Adapters

### Supported Hardware:
- **PEAK PCAN-USB** - Popular, well-supported
- **Kvaser USBcan** - Professional grade
- **CANable** - Budget-friendly, open-source
- **Raspberry Pi + MCP2515 HAT** - Integrated solution
- **Any Linux-compatible CAN adapter**

### Bitrate Settings:
- Most RV systems: **250000 bps**
- Some systems: **500000 bps**
- Check your system documentation

## Performance Benefits

With direct CAN adapter:
- ✅ **Latency**: ~100μs (vs ~10-50ms with TCP)
- ✅ **Reliability**: No network dependency
- ✅ **Throughput**: Higher message rate
- ✅ **Simplicity**: One less component

## Next Steps

1. Add your devices as Things under the bridge
2. Configure channels and items as usual
3. Enjoy faster, more reliable CAN communication!

## Need Help?

- Check logs: `/path/to/openhab/log/openhab.log`
- Enable verbose logging in bridge configuration
- See full documentation: `CAN_ADAPTER_SUPPORT.md`
- Test with: `candump can0` and `cansend can0 123#DEADBEEF`

## Success Checklist

- [ ] CAN interface shows as UP: `ip link show can0`
- [ ] Can see CAN traffic: `candump can0`
- [ ] OpenHAB log shows: "Connected to SocketCAN interface"
- [ ] Bridge status shows ONLINE in OpenHAB UI
- [ ] Devices are discovered/responding

