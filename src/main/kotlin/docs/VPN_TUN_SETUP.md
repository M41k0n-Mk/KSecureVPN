# KSecureVPN — VPN-level Tunneling (TUN/TAP) Setup

This document explains how to enable network-level tunneling using a virtual interface (TUN/L3). The first target is Linux. Windows/macOS notes are included at the end.

Important security note:
- All traffic transported by KSecureVPN is encrypted and authenticated by AES (see `crypt/AESCipher.kt`).
- Do not log keys, plaintext traffic, or sensitive credentials. Use `SecureLogger` which avoids printing secrets.

Overview
- Client and Server exchange raw IPv4 packets encapsulated into the existing encrypted stream.
- Virtual IPs from `10.8.0.0/24` are assigned to clients (server keeps a minimal routing table).
- Packets are routed between clients by the server. Optionally, the server can NAT outbound traffic to the Internet.

Code structure (new modules)
- `tunneling/vpn/VirtualInterface.kt` — abstraction for TUN/TAP devices.
- `tunneling/vpn/Protocol.kt` — frame types for CONTROL and PACKET.
- `tunneling/vpn/PacketFramer.kt` — helpers to send/receive frames over the AES channel.
- `tunneling/vpn/IPv4.kt` — minimal IPv4 parsing helpers.
- `tunneling/vpn/IpPool.kt` — simple /24 IP allocator (10.8.0.0/24 by default).
- `tunneling/vpn/RoutingTable.kt` — maps virtual IPs to session senders.
- `tunneling/vpn/VpnServer.kt` — experimental VPN server (auth, IP assignment, routing between clients).
- `tunneling/vpn/VpnClient.kt` — experimental client using a `VirtualInterface`.
- `tunneling/vpn/stub/MemoryTun.kt` — in-memory TUN used for tests.

Linux (TUN) — quick start
1) Kernel and permissions
   - Ensure `/dev/net/tun` exists and your user can create TUN devices.
   - On Debian/Ubuntu: `sudo apt-get install -y iproute2`.

2) Create and configure a TUN device (server side)
   - Create TUN device `tun0`, assign `10.8.0.1/24`, set MTU:
     ```bash
     sudo ip tuntap add dev tun0 mode tun
     sudo ip addr add 10.8.0.1/24 dev tun0
     sudo ip link set dev tun0 mtu 1500 up
     ```
   - Enable IP forwarding (Linux):
     ```bash
     sudo sysctl -w net.ipv4.ip_forward=1
     ```
   - Optional: NAT traffic from 10.8.0.0/24 to the Internet via `eth0`:
     ```bash
     sudo iptables -t nat -A POSTROUTING -s 10.8.0.0/24 -o eth0 -j MASQUERADE
     sudo iptables -A FORWARD -s 10.8.0.0/24 -o eth0 -j ACCEPT
     sudo iptables -A FORWARD -d 10.8.0.0/24 -m state --state ESTABLISHED,RELATED -i eth0 -j ACCEPT
     ```

3) Client-side TUN configuration (each client)
   - Create client TUN `tun0` and configure the assigned IP (e.g., 10.8.0.2/24):
     ```bash
     sudo ip tuntap add dev tun0 mode tun
     sudo ip addr add 10.8.0.2/24 dev tun0
     sudo ip link set dev tun0 mtu 1500 up
     ```
   - Add route for the VPN network if needed (usually added via the IP/Mask above):
     ```bash
     sudo ip route add 10.8.0.0/24 dev tun0
     ```

4) Connecting KSecureVPN
   - Start the VPN server (experimental): `VpnServer` listens on TCP port `9001`. CLI wiring is not finalized; see code.
   - Start clients using `VpnClient` and a concrete `VirtualInterface` implementation (see notes below).

VirtualInterface implementations
- For Linux, a `VirtualInterface` implementation can be written using JNA/JNR to access `/dev/net/tun` with `IFF_TUN | IFF_NO_PI`.
- This repository currently includes a test-only `MemoryTun` (in-memory) and focuses on protocol/routing. Add a real Linux `TunInterface` implementation as a follow-up:
  - Open `/dev/net/tun`, configure via `TUNSETIFF` ioctl.
  - Perform blocking reads/writes on the fd and adapt to `VirtualInterface` methods.

Windows/macOS notes
- Windows: Use TAP-Windows or Wintun (preferred). A JNI/JNA bridge is required to read/write packets.
- macOS: Use utun interfaces via `SystemConfiguration`/NetworkExtension or `/dev/utunX` with a small native binding.

MTU and fragmentation
- Default MTU set to 1500. The VPN adds minimal overhead inside the encrypted payload, but underlying TCP may fragment further.
- For better performance, consider MSS clamping and/or a slightly smaller MTU (e.g., 1400) to reduce fragmentation over the tunnel.

Security considerations
- Keys are never logged. Use environment variable `KSECUREVPN_KEY` for a 32-byte base64 key.
- Use strong user credentials (PBKDF2-based `PasswordHasher`).
- Do not log packet contents.

Testing
- Unit tests cover: framing, IP allocation, IPv4 parsing, routing table.
- A future integration test can spin up `VpnServer` and two clients with a real TUN, then verify `ping` between clients.
