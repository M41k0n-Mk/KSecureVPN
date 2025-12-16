# KSecureVPN

KSecureVPN is an open-source VPN solution developed in Kotlin, designed for learning, experimentation, and personal portfolio. The project aims to provide a straightforward, modular, and secure virtual private network implementation that demonstrates core networking, cryptography, and authentication principles.

## Goals

- **Educational:** Serve as a learning platform for developers interested in understanding the inner workings of VPNs, network protocols, and security practices. All code is written with clarity and extensive documentation.
- **Personal Use:** Offer a lightweight VPN solution for private use, allowing secure communication between devices over potentially unsafe networks.
- **Portfolio:** Showcase advanced backend and Kotlin development skills, including network programming, concurrency with coroutines, and practical application of cryptography.

## Features (MVP)

- **VPN Tunneling (UDP):** Secure VPN over UDP, encapsulating raw IP packets (lower latency and overhead).
- **Encryption (AEAD):** AES‑GCM with per‑frame sequence number and anti‑replay window (confidentiality + integrity).
- **Authentication:** Username/password (PBKDF2) to control access.
- **Session Tracking:** Each connection has a Session ID for auditing.
- **Secure Logging:** Secure logs with rotation options and without leaking secrets.
- **IP Management:** 10.8.0.0/24 pool and routing table.
- **Server Gateway (Linux):** Server can act as an internet gateway with IP forwarding + NAT (iptables/nftables) and optional UDP/9001 opening (ufw/firewalld).
- **Client Auto‑Networking (Linux):** Client configures TUN, IP/MTU, optional default route, and DNS automatically.
- **Cross‑platform:** Linux and Windows with real TUN; in‑memory fallback otherwise.

## Architecture & Communication

KSecureVPN supports **encrypted P2P communication** between clients via a central server. On Linux, the server can also route traffic to the internet (egress) when NAT/forwarding is enabled (automated via `SystemNetworking`).

### Transport Layer: UDP
KSecureVPN uses **UDP (User Datagram Protocol)** instead of TCP for its transport layer. This choice provides several advantages for VPN implementations:

- **Lower Latency:** UDP eliminates TCP's connection overhead and retransmission delays, resulting in faster packet forwarding.
- **Better Performance on Lossy Networks:** VPNs often operate over unreliable connections; UDP handles packet loss more gracefully without blocking on retransmissions.
- **Reduced Overhead:** No three-way handshake or keep-alive mechanisms, making it lighter for real-time applications.
- **Foundation for Reliability:** While UDP doesn't guarantee delivery, the application layer can add sequence numbers or acknowledgments if needed for critical packets.

However, this means authentication and control messages are sent unreliably. In practice, retries can be implemented at the application level for important frames.

### Current Capabilities ✅
- **Peer-to-Peer Communication**: Clients can exchange IP packets through the encrypted server
- **Automatic IP Assignment**: Each client gets a unique IP from the 10.8.0.0/24 range
- **Packet Routing**: Server maintains routing tables to forward packets between clients
- **Authentication**: Username/password-based access control
- **AEAD + Anti‑replay**: AES‑GCM + per‑frame sequence numbers
- **Server egress (Linux)**: Automated NAT + forwarding (iptables/nftables)

### Testing Communication (Linux)
```bash
# Terminal 1: Start server (Linux; root/CAP_NET_ADMIN recommended)
export KSECUREVPN_KEY=$(head -c 32 /dev/urandom | base64)
export KSECUREVPN_WAN_IFACE=eth0                 # set your WAN interface
export KSECUREVPN_FIREWALL_BACKEND=nftables      # optional: or leave iptables
export KSECUREVPN_FIREWALL_OPEN_PORT=true        # optional
export KSECUREVPN_FIREWALL_PERMANENT=true        # optional (firewalld)
mvn -q exec:java -Dexec.args="server"

# Terminal 2: Connect client Alice (gets 10.8.0.2)
export KSECUREVPN_CLIENT_SET_DEFAULT_ROUTE=true  # optional: route all via VPN
export KSECUREVPN_CLIENT_DNS=8.8.8.8,8.8.4.4     # optional: DNS over the tunnel
KSECUREVPN_KEY=$KSECUREVPN_KEY mvn -q exec:java -Dexec.args="client" &

# Terminal 3: Connect client Bob (gets 10.8.0.3)
KSECUREVPN_KEY=$KSECUREVPN_KEY mvn -q exec:java -Dexec.args="client" &
```

### What Works Today
Clients connected to the same server can communicate with each other using their assigned VPN IPs. On Linux, with NAT/forwarding enabled, clients can also access the internet through the server (sites will see the server's IP).

### Limitations and Notes
- Internet egress is currently implemented for the Linux server only (requires root/CAP_NET_ADMIN and firewall tools installed).
- Client automation currently targets Linux; Windows/macOS pending.
- Symmetric key model for now (no PFS/Noise/TLS handshake yet) — suitable for lab; production hardening on the roadmap.

Notes on virtual interfaces (TUN):
- Linux: Real TUN supported via `/dev/net/tun` (JNA), class `tunneling.vpn.linux.RealTun`
- Windows: Real TUN supported via Wintun, class `tunneling.vpn.windows.WintunTun` (requires `wintun.dll` in PATH or `KSECUREVPN_WINTUN_DLL`)
- macOS: Pending (utun) — fallback to in-memory `MemoryTun` for now

📖 **[Usage Guide & Roadmap](docs/USAGE_GUIDE.md)** - How to use KSecureVPN today and development roadmap

## Architecture

The project follows a layered architecture for clarity and extensibility:

- **Core Layer:** Contains the main VPN logic, session management, and packet handling.
- **Networking Layer:** Manages socket connections, packet encapsulation, and transmission.
- **Security Layer:** Handles encryption/decryption and authentication schemes.
- **Interface Layer:** Provides CLI tools for configuration and usage.

## Project Structure

### Root Directory
- `README.md` - This file with project overview and usage instructions
- `pom.xml` - Maven build configuration with dependencies
- `LICENSE` - MIT license
- `scripts/` - Automation scripts (e.g., E2E tests)
- `examples/` - Example code and demonstrations
- `config/` - Configuration files (users.properties, etc.)

### Source Code (`src/main/kotlin/`)
- `Main.kt` - Application entry point with mode selection
- `VpnDemo.kt` - Comprehensive VPN demonstration with packet exchange
- `LoggingDemo.kt` - Logging and session tracking demonstration

#### `auth/` - Authentication System
- `AuthService.kt` - Core authentication service with user validation
- `PasswordHasher.kt` - PBKDF2 password hashing implementation
- `UserRecord.kt` - User data structures and parsing
- `utils/UserTool.kt` - CLI utility for user management

#### `config/` - Configuration Management
- `Config.kt` - Configuration loading and validation
- `IpAllowlist.kt` - IP address filtering and CIDR support

#### `crypt/` - Cryptography
- `GcmCipher.kt` - AES‑GCM (AEAD) helper
- `AESCipher.kt` - (legacy) AES/CBC helper used for key generation/reading

#### `docs/` - Documentation
- `AUTHENTICATION.md` - Authentication system details
- `LOGGING.md` - Logging configuration and security
- `VPN_TUN_SETUP.md` - VPN tunneling setup guide
- `USAGE_GUIDE.md` - Complete usage guide and development roadmap

#### `logging/` - Logging System
- `SecureLogger.kt` - Secure logging with file protection
- `LogConfig.kt` - Logging configuration management

#### `session/` - Session Management
- `SessionTracker.kt` - Unique session ID generation and tracking

#### `tunneling/` - Networking and Tunneling
- `Utils.kt` - Shared utilities for stream operations
- `vpn/` - VPN-specific components
  - `VirtualInterface.kt` - Abstract interface for TUN/TAP devices
  - `Protocol.kt` - VPN protocol frame definitions
  - `PacketFramer.kt` - Packet framing over encrypted streams
  - `IPv4.kt` - IPv4 packet parsing utilities
  - `IpPool.kt` - IP address pool management
  - `RoutingTable.kt` - VPN routing table implementation
  - `VpnServer.kt` - VPN server with authentication and routing
  - `VpnClient.kt` - VPN client with virtual interface support
  - `linux/RealTun.kt` - Real Linux TUN using `/dev/net/tun` (IFF_TUN | IFF_NO_PI)
  - `windows/WintunTun.kt` - Real Windows TUN via Wintun DLL
  - `stub/MemoryTun.kt` - In-memory TUN implementation used as fallback/testing

### Tests (`src/test/kotlin/`)
- Unit tests for all major components
- Integration tests for VPN functionality
- Security-focused tests (no key printing, etc.)

## Why Kotlin?

Kotlin offers a modern, concise syntax, powerful concurrency primitives (coroutines), and excellent interoperability with Java libraries and platforms. It enables rapid development of robust, cross-platform backend applications, and is a perfect choice for demonstrating advanced software engineering concepts.

## Roadmap

1. **Phase 1:** Implement basic client-server tunneling over TCP.
2. **Phase 2:** Add AES encryption for all transmitted data.
3. **Phase 3:** Integrate simple authentication mechanism.
4. **Phase 4:** Provide CLI commands for user interaction.
5. **Phase 5:** Expand documentation, testing, and add optional features (multi-client support, UDP support, improved configuration).

## Limitations

KSecureVPN is intended primarily for educational and personal use. It is not a replacement for production-grade VPNs, and does not guarantee protection against sophisticated attacks or fulfill enterprise security standards.

## Documentation

- [Usage Guide & Roadmap](docs/USAGE_GUIDE.md) - How to use KSecureVPN today and development roadmap
- [Authentication](docs/AUTHENTICATION.md) - User authentication and credential management
- [Logging](docs/LOGGING.md) - Secure logging and session tracking
- [VPN Setup](docs/VPN_TUN_SETUP.md) - Virtual interface and tunneling configuration

## Running Tests

Run unit tests:
```bash
mvn test
```

Generate test coverage report (HTML):
```bash
mvn kover:report-html
```
Report will be available at `target/site/kover/html/index.html`

## Running End-to-End Tests

An automated E2E test script is provided to verify the complete client-server communication flow:

```bash
./scripts/e2e-test.sh
```

This script will:
- Generate a test encryption key
- Set up environment variables
- Start the server in the background
- Run the client to send a test message
- Verify successful message transmission
- Clean up test artifacts

The test validates authentication, encryption, and message exchange functionality.

## Configuration

KSecureVPN supports multiple configuration methods: environment variables, configuration files, and CLI flags. Environment variables take precedence, followed by config files, then defaults.

### Environment Variables

#### Core Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `KSECUREVPN_KEY` | Generated | Base64-encoded 32-byte AES key (required) |

#### Server Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `KSECUREVPN_BIND` | `127.0.0.1` | Server bind address |
| `KSECUREVPN_PORT` | `9000` | Server port |
| `KSECUREVPN_ALLOWED_IPS` | (empty) | Comma-separated allowed IPs/CIDRs |

#### Logging Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `KSECUREVPN_LOGGING_ENABLED` | `true` | Enable detailed logging |
| `KSECUREVPN_LOG_DIR` | `logs` | Log directory path |
| `KSECUREVPN_LOG_FILE` | `ksecurevpn.log` | Log filename |
| `KSECUREVPN_LOG_MAX_SIZE_MB` | `10` | Max log file size in MB |
| `KSECUREVPN_LOG_STACK_TRACES` | `true` | Include stack traces in logs |

### Configuration Files

#### users.properties
Located in `config/users.properties`, stores user credentials in PBKDF2 format:
```
username=iterations:base64salt:base64hash
```

Use the `UserTool` to create users securely:
```bash
mvn exec:java -Dexec.mainClass="auth.utils.UserToolKt" -Dexec.args="create-user alice"
```

### CLI Flags

The application supports CLI flags for configuration (not fully implemented in current Main.kt):
- `--bind=ADDRESS` - Server bind address
- `--port=PORT` - Server port
- `--allowed=CSV` - Allowed IPs/CIDRs
- `--config=PATH` - Path to config file

### Key Management

For security, keys are never printed. Generate a development key:
```bash
head -c 32 /dev/urandom | base64 | tee ksecurevpn.key
chmod 600 ksecurevpn.key
export KSECUREVPN_KEY=$(cat ksecurevpn.key)
```

For production, use a secrets manager (Vault, KMS) to inject `KSECUREVPN_KEY`.

## Understanding IP Allowlist

The `KSECUREVPN_ALLOWED_IPS` controls **which client IPs can connect** to your VPN server. This is an access control layer that works **before authentication**.

### How it works:
1. Client tries to connect → Server checks client's source IP
2. If IP is in allowlist → Connection accepted, proceed to authentication
3. If IP is NOT in allowlist → Connection rejected immediately

### Practical Example - AWS Server:
```bash
# Server running on AWS EC2 (public IP: 54.123.45.67)
export KSECUREVPN_BIND="0.0.0.0"                    # Listen on all interfaces
export KSECUREVPN_PORT="9000"                       # Port 9000
export KSECUREVPN_ALLOWED_IPS="189.45.67.0/24,203.45.123.89"  # Allow these client IPs
java -jar ksecurevpn.jar server

# Your computer (IP: 189.45.67.89) connecting to the server
java -jar ksecurevpn.jar --bind=54.123.45.67 --port=9000 client
```

### Common Scenarios:
- **Corporate**: `"203.45.67.0/24,198.51.100.0/24"` (office networks only)
- **Personal**: `"189.45.123.89,177.67.89.123"` (home and work IPs)
- **Development**: `""` (empty = allow all, but bind to 127.0.0.1 by default)

Discover your public IP: `curl ifconfig.me`

### Security Benefits:
- **Defense in depth**: Even with stolen credentials, attacker needs allowed IP
- **Reduce attacks**: Blocks brute force attempts from unauthorized IPs  
- **Performance**: Rejects invalid connections immediately

## License

MIT License. See LICENSE file for details.

---

**Disclaimer:** This project is for educational and personal experimentation only. Do not use in production environments without thorough security review and testing.
