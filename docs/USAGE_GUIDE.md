# KSecureVPN - Usage Guide & Development Roadmap

## What KSecureVPN Does Today ✅

KSecureVPN is a working VPN prototype that creates an **encrypted overlay network** for peer-to-peer communication. It's not yet a full internet VPN, but you can use it to connect multiple clients that communicate securely through a central server.

### Current Capabilities

**✅ Encrypted Communication**: All traffic between clients and server is AES-encrypted
**✅ Peer-to-Peer Networking**: Connected clients can exchange IP packets
**✅ Automatic IP Assignment**: Each client gets a unique IP (10.8.0.2, 10.8.0.3, etc.)
**✅ Authentication**: Username/password access control
**✅ Session Management**: Connection tracking and automatic cleanup

### How to Use It Right Now

#### Quick Start

1. **Generate encryption key**:
```bash
export KSECUREVPN_KEY=$(head -c 32 /dev/urandom | base64)
echo "Key: $KSECUREVPN_KEY"
```

2. **Start the server**:
```bash
mvn exec:java -Dexec.args="server"
```

3. **Connect clients** (in different terminals):
```bash
# Client 1 (gets IP 10.8.0.2)
KSECUREVPN_KEY=$KSECUREVPN_KEY mvn exec:java -Dexec.args="client" &

# Client 2 (gets IP 10.8.0.3)
KSECUREVPN_KEY=$KSECUREVPN_KEY mvn exec:java -Dexec.args="client" &
```

#### Demo Mode

Run the built-in demonstration:
```bash
mvn exec:java -Dexec.args="vpn-demo"
```

This shows packet exchange between simulated clients Alice and Bob.

## How Communication Works

### Network Flow
```
Client A (10.8.0.2) ────[Encrypted TCP]──── Server ────[Encrypted TCP]──── Client B (10.8.0.3)
        │                                               │
        └─────────────── Virtual Network ───────────────┘
```

### What Happens When You Connect

1. **Client connects** → TCP connection to server:9001
2. **Authentication** → Sends username/password
3. **IP Assignment** → Server gives unique IP from 10.8.0.0/24
4. **Route Registration** → Server adds client to routing table
5. **Ready** → Client can now send/receive packets to other clients

### Packet Routing

When Client A sends a packet to Client B's IP:
1. Client A encapsulates IP packet in encrypted frame
2. Sends to server via TCP
3. Server decrypts, reads destination IP
4. Looks up route in routing table
5. Forwards encrypted packet to Client B
6. Client B decrypts and receives the packet

## What You CAN Do Today

### ✅ Test Peer Communication
- Connect multiple clients to same server
- Send packets between clients using their VPN IPs
- All traffic is encrypted end-to-end

### ✅ Learn VPN Internals
- Study the protocol implementation
- Understand packet routing and encryption
- Experiment with network programming

### ✅ Use as Secure Chat/Overlay Network
- Create private networks for specific applications
- Secure communication between devices

## What You CANNOT Do Yet (Roadmap)

### 🔴 Access Internet Through VPN
**Problem**: Sites see your real IP, not the server's IP
**Solution Needed**: NAT/Masquerading on server + routing configuration

### 🔴 Automatic Client Setup
**Problem**: Manual key distribution and complex setup
**Solution Needed**: Configuration files + automatic routing

### 🔴 Real Network Integration
**Problem**: Uses in-memory TUN simulation
**Solution Needed**: Real TUN device integration

## Development Roadmap

### Phase 1: Real TUN Interface (High Priority)
- Replace `MemoryTun` with real `/dev/net/tun` device
- Integrate with OS kernel networking
- Test on Linux systems

### Phase 2: Internet Access
- Add iptables NAT rules on server
- Enable IP forwarding
- Configure server-side routing

### Phase 3: Client Automation
- Create `.ovpn`-style config files
- Automatic route setup on clients
- Cross-platform client (Windows/macOS/Linux)

### Phase 4: Production Security
- Certificate-based authentication
- Key rotation and perfect forward secrecy
- DDoS protection and rate limiting

### Phase 5: Advanced Features
- UDP support for better performance
- Multi-server/high availability
- GUI client application

## Testing Current Functionality

### Automated Tests
```bash
# Run all tests
mvn test

# Run with linting
mvn test ktlint:check

# Run E2E tests (GitHub Actions)
# Tests server startup and client connection
```

### Manual Testing
```bash
# 1. Start server
mvn exec:java -Dexec.args="server"

# 2. In another terminal, connect client
KSECUREVPN_KEY=$KSECUREVPN_KEY mvn exec:java -Dexec.args="client"

# 3. Check server logs - should show client connection
# 4. Check client - should show successful authentication
```

## Architecture Overview

### Core Components
- **VpnServer**: Manages connections, authentication, routing
- **VpnClient**: Connects to server, handles virtual networking
- **Protocol**: Custom frame-based communication protocol
- **RoutingTable**: Server-side packet forwarding
- **VirtualInterface**: Abstract TUN device interface

### Security Model
- **Encryption**: AES with unique IV per frame
- **Authentication**: PBKDF2 password hashing
- **Session Tracking**: Unique IDs for audit trails
- **Key Distribution**: Environment variable (not production-ready)

## Contributing

See GitHub Issues for specific implementation tasks:
- [#52](https://github.com/M41k0n-Mk/KSecureVPN/issues/52) - Real TUN Interface
- [#53](https://github.com/M41k0n-Mk/KSecureVPN/issues/53) - System Routing
- [#54](https://github.com/M41k0n-Mk/KSecureVPN/issues/54) - NAT/Masquerading
- And more...

## Conclusion

KSecureVPN gives you a working foundation for understanding VPN internals and creating secure peer-to-peer networks. It's perfect for learning, experimentation, and building custom networking solutions. With additional development, it can become a full-featured VPN for internet access.</content>
<parameter name="filePath">/workspaces/KSecureVPN/docs/ARCHITECTURE.md