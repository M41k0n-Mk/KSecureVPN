# KSecureVPN - Usage Guide & Development Roadmap

## What KSecureVPN Does Today ✅

KSecureVPN is a functional VPN prototype that creates an encrypted overlay network between clients through a central server. The current implementation uses UDP as the transport and AES‑GCM (AEAD) for confidentiality and integrity, with per‑frame sequence numbers and an anti‑replay window. On Linux, the server can act as an internet gateway (egress) when IP forwarding and NAT (iptables/nftables) are enabled — automation for these rules is built‑in.

### Current capabilities

**✅ Authenticated encryption (AEAD)**: Traffic protected with AES‑GCM + authentication tag
**✅ Anti‑replay**: Per‑frame sequence number and sliding window
**✅ UDP transport**: Low latency and better performance on lossy networks
**✅ Server‑mediated P2P**: IP packet forwarding between clients
**✅ Automatic IP assignment**: 10.8.0.0/24 pool (10.8.0.1 reserved as gateway)
**✅ Authentication**: Username/password with PBKDF2
**✅ Real TUN**: Linux (`RealTun`) and Windows (`Wintun`) with in‑memory fallback
**✅ Server as gateway (Linux)**: IP forwarding + NAT (iptables or nftables) automation
**✅ Linux client auto‑config**: Bring up TUN, set IP/MTU, optional default route and DNS

### How to use now

#### Quick Start (Linux)

1. **Generate encryption key**:
```bash
export KSECUREVPN_KEY=$(head -c 32 /dev/urandom | base64)
echo "Key: $KSECUREVPN_KEY"
```

2. **Start the server** (Linux; requires root/CAP_NET_ADMIN for networking changes):
```bash
# Interface WAN para NAT (ex.: eth0)
export KSECUREVPN_WAN_IFACE=eth0
# (opcional) escolher backend de firewall/NAT: iptables (padrão) ou nftables
export KSECUREVPN_FIREWALL_BACKEND=nftables
# (opcional) abrir UDP/9001 no ufw/firewalld (se instalados)
export KSECUREVPN_FIREWALL_OPEN_PORT=true
# (opcional) tornar regra permanente (firewalld)
export KSECUREVPN_FIREWALL_PERMANENT=true

mvn -q exec:java -Dexec.args="server"
```

3. **Connect clients** (in separate terminals):
```bash
# Client 1 (gets 10.8.0.2). Optional: set default route and DNS on Linux client
export KSECUREVPN_CLIENT_SET_DEFAULT_ROUTE=true
export KSECUREVPN_CLIENT_DNS=8.8.8.8,8.8.4.4
KSECUREVPN_KEY=$KSECUREVPN_KEY mvn -q exec:java -Dexec.args="client" &

# Client 2 (gets 10.8.0.3)
KSECUREVPN_KEY=$KSECUREVPN_KEY mvn -q exec:java -Dexec.args="client" &
```

#### Demo Mode

Run the built-in demonstration:
```bash
mvn exec:java -Dexec.args="vpn-demo"
```

This shows packet exchange between simulated clients Alice and Bob.

## How communication works

### Network flow
```
Client A (10.8.0.2) ────[Encrypted UDP/AEAD]──── Server ────[Encrypted UDP/AEAD]──── Client B (10.8.0.3)
        │                                               │
        └─────────────── Virtual Network ───────────────┘
```

### What happens when you connect

1. **Client connects** → UDP para server:9001
2. **Authentication** → Sends username/password
3. **IP Assignment** → Server gives unique IP from 10.8.0.0/24
4. **Route Registration** → Server adds client to routing table
5. **Ready** → Client can now send/receive packets to other clients

### Packet Routing

When Client A sends a packet to Client B's IP:
1. Client A encapsulates IP packet in encrypted frame
2. Sends to the server via UDP
3. Server decrypts, reads destination IP
4. Looks up route in routing table
5. Forwards encrypted packet to Client B
6. Client B decrypts and receives the packet

## What you can do today

### ✅ Test peer communication
- Connect multiple clients to same server
- Send packets between clients using their VPN IPs
- All traffic is encrypted end-to-end

### ✅ Learn VPN internals
- Study the protocol implementation
- Understand packet routing and encryption
- Experiment with network programming

### ✅ Use as secure overlay
- Create private networks for specific applications
- Secure communication between devices

## Roadmap (updated)

### 🟢 Internet via VPN (Linux)
Available when the Linux server has `KSECUREVPN_WAN_IFACE` set. The server brings up the TUN, enables `net.ipv4.ip_forward=1`, and applies NAT/FORWARD via iptables or nftables. The Linux client can set default route via VPN and DNS.

### 🟢 Client automation (Linux)
After `IP_ASSIGN`, the client configures IP/MTU, optional default route, and DNS. Windows/macOS: pending.

### 🟡 Real network integration (cross‑platform)
Today:
- Linux: real TUN `/dev/net/tun` (`tunneling.vpn.linux.RealTun`).
- Windows: Wintun (`tunneling.vpn.windows.WintunTun`) with `wintun.dll`.
- Others (e.g., macOS): no real TUN — uses `MemoryTun`.

Next steps:
- Implement utun (macOS) and equivalent automation.

## Development Roadmap

### Phase 1: Real TUN (updated)
- Linux: DONE — `/dev/net/tun` via JNA (`RealTun`).
- Windows: DONE — Wintun via JNA (`WintunTun`).
- macOS: PENDING — utun.

### Phase 2: Internet access (Linux)
- DONE — iptables/nftables NAT + IP forwarding + FORWARD rules via `SystemNetworking`.

### Phase 3: Client automation
- Linux: DONE — IP/MTU/optional default route and DNS.
- Windows/macOS: PENDING.

### Phase 4: Production security
- In progress: transport with AEAD (AES‑GCM) + anti‑replay (completed).
- Next: PFS (Noise/TLS 1.3), key rotation, rate limiting and anti‑DoS.

### Phase 5: Advanced features
- Multi‑server/HA, metrics, reconnection/keepalive, GUI.

## Testing

### Automated tests
```bash
# Run all tests
mvn test

# Run with linting
mvn test ktlint:check

# Run E2E tests (GitHub Actions)
# Tests server startup and client connection
```

Notes on TUN tests:
- Testes Linux e Windows que tocam TUN real são condicionais:
  - Linux: executados apenas quando `/dev/net/tun` existe e permissões permitem. O smoke de I/O requer `ENABLE_TUN_TESTS=true`.
  - Windows: executados apenas quando `wintun.dll` está disponível. O smoke de I/O requer `ENABLE_WINTUN_TESTS=true`.

### Manual smoke test
```bash
# 1) Server (Linux)
export KSECUREVPN_KEY=$(head -c 32 /dev/urandom | base64)
export KSECUREVPN_WAN_IFACE=eth0
mvn -q exec:java -Dexec.args="server"

# 2) Client (Linux)
export KSECUREVPN_KEY=... # same key
export KSECUREVPN_CLIENT_SET_DEFAULT_ROUTE=true
export KSECUREVPN_CLIENT_DNS=8.8.8.8,8.8.4.4
mvn -q exec:java -Dexec.args="client"

# 3) Check public IP as seen by the client
curl -4 https://ifconfig.co    # should show the server's IP
```

## Architecture overview

### Core components
- **VpnServer**: Manages connections, authentication, routing
- **VpnClient**: Connects to server, handles virtual networking
- **Protocol**: Custom frame-based communication protocol
- **RoutingTable**: Server-side packet forwarding
- **VirtualInterface**: Abstract TUN device interface

### Security model
- **Encryption**: AES‑GCM (AEAD) with 12‑byte nonce and 16‑byte tag
- **Authentication**: PBKDF2 password hashing
- **Session Tracking**: Unique IDs for audit trails
- **Key Distribution**: Environment variable (not production‑ready)

## Contributing

See GitHub Issues for specific implementation tasks:
- [#52](https://github.com/M41k0n-Mk/KSecureVPN/issues/52) - Real TUN Interface
- [#53](https://github.com/M41k0n-Mk/KSecureVPN/issues/53) - System Routing
- [#54](https://github.com/M41k0n-Mk/KSecureVPN/issues/54) - NAT/Masquerading
- And more...

## Conclusion

KSecureVPN gives you a working foundation for understanding VPN internals and creating secure peer-to-peer networks. It's perfect for learning, experimentation, and building custom networking solutions. With additional development, it can become a full-featured VPN for internet access.