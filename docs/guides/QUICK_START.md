# IDS MyRV Binding - Quick Start Guide

This guide walks you through getting the binding operational from scratch. For build and deployment details, see [SETUP_GUIDE.md](SETUP_GUIDE.md) and [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md).

## Before You Begin

Ensure you have:

- **OpenHAB 5** installed and running
- **Binding deployed** – JAR in `addons/` directory (see [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md))
- **CAN bus access** – Either a TCP gateway on your network, or a SocketCAN adapter (see [SOCKETCAN_QUICKSTART.md](SOCKETCAN_QUICKSTART.md) for direct CAN)

**First time?** Build the JAR (`mvn clean package`), copy to `addons/`, and restart OpenHAB. Wait ~30 seconds for the bundle to load.

---

## Step 1: Verify Bundle is Active

Check that the binding loaded:

```
bundle:list | grep idsmyrv
```

(OpenHAB console: `ssh -p 8101 openhab@localhost`)

Should show: `Active` status. If not, see [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md).

---

## Step 2: Add Gateway Thing

You must add the gateway manually; it will not appear in the Inbox automatically.

### Via OpenHAB UI

1. **Settings** → **Things**
2. Click the **"+"** button (bottom right)
3. Search for **"IDS"** or find **"IDS MyRV Binding"**
4. Select **"IDS MyRV Gateway"**
5. Configure:
   - **Connection Type**: `TCP Gateway` (or `Direct SocketCAN` if using a CAN adapter)
   - **IP Address**: Your gateway IP (e.g., `192.168.1.100`) — *TCP only*
   - **Port**: `8888` — *TCP only*
   - **CAN Interface**: `can0` — *SocketCAN only*
   - **Source Address**: `1` (default)
   - **Verbose**: `false` (enable for debugging)
6. Click **"CREATE"**
7. Wait for status to show **ONLINE** if the gateway is reachable

### Via Text Configuration

Create or edit `things/idsmyrv.things` (path varies: `conf/things/` in OpenHAB, or your configured config dir):

**TCP Gateway:**
```
Bridge idsmyrv:gateway:mygateway "RV CAN Gateway" [
    connectionType="tcp",
    ipAddress="192.168.1.100",
    port=8888,
    sourceAddress=1,
    verbose=false
]
```

**SocketCAN (direct CAN adapter):**
```
Bridge idsmyrv:gateway:mygateway "RV CAN Gateway" [
    connectionType="socketcan",
    canInterface="can0",
    sourceAddress=1,
    verbose=false
]
```

---

## Step 3: Add Device Things

Once the gateway is **ONLINE**, add devices.

### Option A: From Discovery (Inbox)

The binding scans the CAN bus. Discovered devices may appear in **Settings → Things → Inbox**. Add them from there if they show up.

### Option B: Add Manually

If you know the device address, add it manually:

**Via UI:**
1. Click your **Gateway Thing**
2. Click **"+"** to add a child thing
3. Choose device type (Dimmable Light, RGB Light, Tank Sensor, etc.)
4. Enter **Device Address** (e.g., `92`)
5. Click **CREATE**

**Via text config** (add under the Bridge):
```
Bridge idsmyrv:gateway:mygateway "RV CAN Gateway" [
    connectionType="tcp",
    ipAddress="192.168.1.100",
    port=8888
] {
    Thing light bedroom "Bedroom Light" [ address=92 ]
    Thing light kitchen "Kitchen Light" [ address=42 ]
    Thing tanksensor freshwater "Fresh Water" [ address=50 ]
}
```

**Finding addresses:** Device addresses are assigned by the RV manufacturer. Check your RV manual, existing OneControl app, or enable verbose logging and watch the bus traffic when devices report in.

---

## Step 4: Link Items and Test

Create Items and link them to channels so you can control devices.

### Via UI

1. **Settings** → **Items** → **+**
2. Create a **Switch** item:
   - Name: `Bedroom_Light_Switch`
   - Link to: `idsmyrv:light:mygateway:bedroom:switch`
3. Create a **Dimmer** item:
   - Name: `Bedroom_Light_Brightness`
   - Link to: `idsmyrv:light:mygateway:bedroom:brightness`

*Note: The channel path includes your bridge and thing IDs. If you used different IDs (e.g. `mainbridge` instead of `mygateway`), pick the matching channel from the dropdown.*

### Via Items File

Create or edit `items/idsmyrv.items`:

```
Switch Bedroom_Light_Switch "Bedroom Light" <light> {
    channel="idsmyrv:light:mygateway:bedroom:switch"
}
Dimmer Bedroom_Light_Brightness "Bedroom Brightness" <slider> {
    channel="idsmyrv:light:mygateway:bedroom:brightness"
}
```

### Test

Use the main UI or a sitemap to toggle the switch and adjust brightness. The physical device should respond.

---

## Troubleshooting

### Binding doesn't appear when adding a thing

- Run `bundle:list | grep idsmyrv` — should show `Active`
- Restart OpenHAB:
  - macOS (Homebrew): `brew services restart openhab`
  - Linux (systemd): `sudo systemctl restart openhab`
- Check logs: `tail -f /var/log/openhab/openhab.log` (path varies by install)

### Gateway stays OFFLINE

- **TCP:** Verify connectivity: `ping 192.168.1.100` and `nc -zv 192.168.1.100 8888`
- **SocketCAN:** Ensure interface is up: `ip link show can0`
- Enable **Verbose** in the gateway config and check logs

### Commands don't work / device doesn't respond

- Confirm the device address is correct
- Enable verbose logging and look for session/key errors
- See [GATEWAY_TROUBLESHOOTING.md](GATEWAY_TROUBLESHOOTING.md)

---

## Checking Logs

```bash
# Tail logs (adjust path for your install)
tail -f /var/log/openhab/openhab.log

# Filter for this binding
tail -f /var/log/openhab/openhab.log | grep -i idsmyrv

# OpenHAB console
ssh -p 8101 openhab@localhost

# In console: enable debug, then watch
openhab> log:set DEBUG org.openhab.binding.idsmyrv
openhab> log:tail
```

## Expected Log Messages

When everything works:

```
[INFO] Bundle org.openhab.binding.idsmyrv started
[INFO] Connecting to CAN gateway at 192.168.1.100:8888
[INFO] Successfully connected to CAN gateway
[DEBUG] Requesting seed from device 92...
[DEBUG] Session opened with device 92
[DEBUG] Sent ON command to light 92
[DEBUG] Light 92 state updated: ON
```

---

## Next Steps

- Add more devices (lights, tanks, HVAC, relays)
- Create [automation rules](https://www.openhab.org/docs/tutorial/rules.html)
- Build [dashboards](https://www.openhab.org/docs/tutorial/ui.html)
- Add voice control (Alexa, Google Assistant)

---

**More help:** [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) · [GATEWAY_TROUBLESHOOTING.md](GATEWAY_TROUBLESHOOTING.md) · [DOCS_INDEX.md](../../DOCS_INDEX.md)
