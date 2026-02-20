#!/bin/bash
#
# IDS MyRV Binding Troubleshooting Script
#
# Environment variables (optional - override if your OpenHAB paths differ):
#   OPENHAB_ADDONS    - OpenHAB addons directory (default: /usr/local/var/lib/openhab/addons)
#   OPENHAB_LOG       - OpenHAB main log file (default: /usr/local/var/log/openhab/openhab.log)
#   OPENHAB_EVENTS_LOG - OpenHAB events log (default: /usr/local/var/log/openhab/events.log)
#
# Usage:
#   ./scripts/troubleshoot.sh           # Use defaults
#   OPENHAB_ADDONS=/custom/path ./scripts/troubleshoot.sh
#   ./scripts/troubleshoot.sh --help    # Show usage and env vars
#

print_usage() {
    echo "Usage: $0 [--help]"
    echo ""
    echo "Environment variables (optional):"
    echo "  OPENHAB_ADDONS      Addons directory. Default: /usr/local/var/lib/openhab/addons"
    echo "  OPENHAB_LOG         Main log file. Default: /usr/local/var/log/openhab/openhab.log"
    echo "  OPENHAB_EVENTS_LOG   Events log. Default: /usr/local/var/log/openhab/events.log"
    echo ""
    echo "Example: OPENHAB_ADDONS=/opt/openhab/addons ./scripts/troubleshoot.sh"
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    print_usage
    exit 0
fi

echo "========================================"
echo "IDS MyRV Binding Troubleshooting"
echo "========================================"
echo ""

ADDONS_DIR="${OPENHAB_ADDONS:-/usr/local/var/lib/openhab/addons}"
LOG_FILE="${OPENHAB_LOG:-/usr/local/var/log/openhab/openhab.log}"
EVENTS_LOG="${OPENHAB_EVENTS_LOG:-/usr/local/var/log/openhab/events.log}"

# Show which paths we're using (helps user know if they need to set env vars)
echo "Paths in use (set env vars to override):"
echo "  OPENHAB_ADDONS:      $ADDONS_DIR"
echo "  OPENHAB_LOG:         $LOG_FILE"
echo "  OPENHAB_EVENTS_LOG:  $EVENTS_LOG"
echo "  (Set these env vars before running to use different paths)"
echo ""

# 1. Check if bundle is deployed
echo "1. Checking if JAR is deployed..."
if [ -f "$ADDONS_DIR/org.openhab.binding.idsmyrv-5.0.2-SNAPSHOT.jar" ]; then
    echo "   ✅ JAR found in addons directory"
    ls -lh "$ADDONS_DIR/org.openhab.binding.idsmyrv-5.0.2-SNAPSHOT.jar"
else
    echo "   ❌ JAR NOT found in addons directory"
    echo "   Copy it with: cp target/org.openhab.binding.idsmyrv-5.0.2-SNAPSHOT.jar $ADDONS_DIR/"
    if [[ ! -d "$ADDONS_DIR" ]]; then
        echo "   ⚠️  Addons directory does not exist. Set OPENHAB_ADDONS to your addons path."
    fi
fi
echo ""

# 2. Check bundle status via OpenHAB console
echo "2. To check bundle status, run in OpenHAB console:"
echo "   ssh -p 8101 openhab@localhost  (password: habopen)"
echo "   Then run: bundle:list | grep idsmyrv"
echo ""
echo "   Expected output:"
echo "   XXX | Active | 80 | 5.0.2.SNAPSHOT | org.openhab.binding.idsmyrv"
echo ""

# 3. Check OpenHAB logs
echo "3. Checking OpenHAB logs for errors..."
if [ -f "$LOG_FILE" ]; then
    echo "   Recent binding-related log entries:"
    tail -100 "$LOG_FILE" | grep -i "idsmyrv\|binding.*idsmyrv" | tail -10
    echo ""
    echo "   Recent errors:"
    tail -100 "$LOG_FILE" | grep -i "error\|exception" | grep -i "idsmyrv" | tail -5
else
    echo "   Log file not found at $LOG_FILE"
    echo "   Set OPENHAB_LOG if your log file is elsewhere."
fi
echo ""

# 4. Check events log
echo "4. Checking events log..."
if [ -f "$EVENTS_LOG" ]; then
    echo "   Recent events:"
    tail -50 "$EVENTS_LOG" | grep -i "idsmyrv" | tail -10
else
    echo "   Events log not found at $EVENTS_LOG"
    echo "   Set OPENHAB_EVENTS_LOG if your events log is elsewhere."
fi
echo ""

# 5. Instructions
echo "========================================"
echo "Manual Checks to Perform:"
echo "========================================"
echo ""
echo "A. Check bundle in OpenHAB console:"
echo "   ssh -p 8101 openhab@localhost"
echo "   bundle:list | grep idsmyrv"
echo "   bundle:headers <bundle-id>"
echo ""
echo "B. Check if binding is recognized:"
echo "   In OpenHAB UI: Settings → Things → + → Search for 'IDS'"
echo ""
echo "C. Enable debug logging:"
echo "   ssh -p 8101 openhab@localhost"
echo "   log:set DEBUG org.openhab.binding.idsmyrv"
echo "   log:tail"
echo ""
echo "D. Restart OpenHAB:"
echo "   brew services restart openhab"
echo "   Wait 30 seconds, then check again"
echo ""
echo "E. Check jar contents:"
echo "   unzip -l target/org.openhab.binding.idsmyrv-5.0.2-SNAPSHOT.jar | grep OH-INF"
echo ""

