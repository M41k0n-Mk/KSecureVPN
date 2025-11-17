#!/bin/bash

# KSecureVPN End-to-End Test Script
# This script runs the VPN demo and verifies successful packet exchange.

set -e

echo "=== KSecureVPN E2E Test ==="
echo

# Clean up any previous test artifacts
rm -f ksecurevpn.key
rm -rf logs/

# Build the project
echo "1. Building the project..."
mvn clean compile -q
echo "✅ Build successful"
echo

# Run VPN demo
echo "2. Running VPN demo..."
mvn exec:java -Dexec.mainClass="VpnDemoKt" > vpn_demo_output.log 2>&1
echo "✅ VPN demo executed"
echo

# Verify VPN demo output
echo "3. Verifying VPN demo output..."
if grep -q "Demo concluído com sucesso!" vpn_demo_output.log; then
    echo "✅ SUCCESS: VPN demo completed successfully"
else
    echo "❌ FAILURE: VPN demo did not complete successfully"
    echo "VPN demo output:"
    cat vpn_demo_output.log
    exit 1
fi

# Check for packet exchange
if grep -q "📥.*recebeu" vpn_demo_output.log && grep -q "📤.*enviou" vpn_demo_output.log; then
    echo "✅ SUCCESS: Packet exchange detected"
else
    echo "❌ FAILURE: No packet exchange found"
    exit 1
fi

echo
echo "=== E2E Test PASSED ==="
echo "✅ VPN demo execution: OK"
echo "✅ Packet exchange: OK"
echo
echo "Test artifacts:"
echo "- VPN demo output: vpn_demo_output.log"
echo
echo "To run manually: mvn exec:java -Dexec.mainClass=\"VpnDemoKt\""