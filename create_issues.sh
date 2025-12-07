#!/bin/bash

# Script para criar issues faltantes no GitHub usando gh CLI
# Execute: chmod +x create_issues.sh && ./create_issues.sh

echo "Criando issues faltantes no repositório KSecureVPN..."

# Issue 1: Migrate Transport to UDP
gh issue create \
  --title "Migrate Transport to UDP" \
  --body "Replace TCP with UDP for better performance and reduced latency in VPN tunnels.

**Requirements:**
- Switch from ServerSocket/Socket to DatagramSocket.
- Handle packet loss, reordering, and fragmentation at application level.
- Update PacketFramer for UDP (no persistent connections).
- Add sequence numbers for reliability if needed.

**Files to modify:**
- src/main/kotlin/tunneling/vpn/VpnServer.kt
- src/main/kotlin/tunneling/vpn/VpnClient.kt
- src/main/kotlin/tunneling/PacketFramer.kt

**Testing:**
- Test on high-latency networks.
- Verify no packet loss in demos.

**Labels:** enhancement, performance"

# Issue 2: Add MTU and Packet Fragmentation Support
gh issue create \
  --title "Add MTU and Packet Fragmentation Support" \
  --body "Handle Maximum Transmission Unit (MTU) detection and packet fragmentation for large packets.

**Requirements:**
- Detect MTU of real TUN interface.
- Fragment oversized packets before sending.
- Reassemble on receive.
- Support IPv4 fragmentation headers.

**Files to modify:**
- src/main/kotlin/tunneling/vpn/VirtualInterface.kt
- src/main/kotlin/tunneling/vpn/VpnClient.kt
- src/main/kotlin/tunneling/vpn/VpnServer.kt

**Testing:**
- Test with packets > 1500 bytes.
- Verify on different network MTUs.

**Labels:** enhancement, networking"

# Issue 3: Add IPv6 Support
gh issue create \
  --title "Add IPv6 Support" \
  --body "Extend VPN to support IPv6 packets and addresses.

**Requirements:**
- Update IPv4.kt to handle IPv6 parsing/routing.
- Support IPv6 in RoutingTable.
- Assign IPv6 addresses to clients.
- Handle dual-stack if needed.

**Files to modify:**
- src/main/kotlin/tunneling/vpn/IPv4.kt (rename to IP.kt?)
- src/main/kotlin/tunneling/vpn/RoutingTable.kt
- src/main/kotlin/tunneling/vpn/VpnServer.kt

**Testing:**
- Test IPv6-only networks.
- Verify routing between IPv6 clients.

**Labels:** enhancement, networking"

# Issue 4: Implement Packet Compression
gh issue create \
  --title "Implement Packet Compression" \
  --body "Add compression to reduce bandwidth usage in VPN tunnels.

**Requirements:**
- Use LZ4 or similar for fast compression.
- Compress before encryption in PacketFramer.
- Decompress after decryption.
- Optional: Configurable compression level.

**Files to modify:**
- src/main/kotlin/tunneling/PacketFramer.kt
- Add dependency: org.lz4:lz4-java

**Testing:**
- Measure bandwidth reduction.
- Test compression ratio on different payloads.

**Labels:** enhancement, performance"

# Issue 5: Add Real Integration Tests with TUN Device
gh issue create \
  --title "Add Real Integration Tests with TUN Device" \
  --body "Create end-to-end tests using real TUN interfaces instead of MemoryTun.

**Requirements:**
- Test on Linux with /dev/net/tun.
- Simulate full client-server tunnel.
- Verify packet forwarding, encryption, and routing.
- Run in CI with Docker or VM.

**Files to modify:**
- src/test/kotlin/... (new test files)
- Use RealTun from #52.

**Testing:**
- Automated tests for connectivity.
- Mock TUN if real not available.

**Labels:** test, integration
**Dependencies:** #52"

echo "Issues criadas com sucesso!"