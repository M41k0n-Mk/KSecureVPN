# KSecureVPN

KSecureVPN is an open-source VPN solution developed in Kotlin, designed for learning, experimentation, and personal portfolio. The project aims to provide a straightforward, modular, and secure virtual private network implementation that demonstrates core networking, cryptography, and authentication principles.

## Goals

- **Educational:** Serve as a learning platform for developers interested in understanding the inner workings of VPNs, network protocols, and security practices. All code is written with clarity and extensive documentation.
- **Personal Use:** Offer a lightweight VPN solution for private use, allowing secure communication between devices over potentially unsafe networks.
- **Portfolio:** Showcase advanced backend and Kotlin development skills, including network programming, concurrency with coroutines, and practical application of cryptography.

## Features (MVP)

- **Basic Tunneling:** Establishes secure client-server communication using TCP sockets, encapsulating IP packets.
- **Encryption:** Implements AES-based encryption to protect data in transit.
- **Simple Authentication:** Supports username/password authentication to restrict access.
- **Session Tracking:** Each connection is assigned a unique session ID for debugging and audit purposes.
- **Secure Logging:** Detailed debug logs with decryption error tracking, protected from unauthorized access. Console output remains generic and safe.
- **Modular Design:** Organized in clear modules: networking, cryptography, authentication, configuration.
- **Cross-platform:** Runs on any JVM-supported system (Linux, Windows, macOS).

## Architecture

The project follows a layered architecture for clarity and extensibility:

- **Core Layer:** Contains the main VPN logic, session management, and packet handling.
- **Networking Layer:** Manages socket connections, packet encapsulation, and transmission.
- **Security Layer:** Handles encryption/decryption and authentication schemes.
- **Interface Layer:** Provides CLI tools for configuration and usage.

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

## Getting Started

- Clone the repository
- Build with Maven: `mvn clean install`
- Configure logging (optional): See [docs/LOGGING.md](src/main/kotlin/docs/LOGGING.md)
- Run the server and client from CLI
- Follow documentation for configuration and usage examples

## Configuration

You can configure bind address, port and allowed client IPs using environment variables, an optional config file, or CLI flags. Recommended secure defaults bind the server to loopback (127.0.0.1) and use explicit allowlists where needed.

Environment variables:
- `KSECUREVPN_BIND`: bind address (default: `127.0.0.1`)
- `KSECUREVPN_PORT`: port (default: `9000`)
- `KSECUREVPN_ALLOWED_IPS`: comma-separated list of allowed IPs or CIDRs (e.g. `192.168.1.0/24,10.0.0.5`). Empty means allow all — prefer explicit values in production.
- `KSECUREVPN_CONFIG`: optional path to a properties file with keys `bind`, `port`, `allowed` matching the same semantics.

CLI flags (place before the mode argument):
- `--bind=ADDRESS`
- `--port=PORT`
- `--allowed=CSV`
- `--config=/path/to/file`

Examples:
- Start server bound to loopback (default):
	`java -jar ksecurevpn.jar server`
- Start server on all interfaces (not recommended):
	`java -jar ksecurevpn.jar --bind=0.0.0.0 --port=9000 server`
- Start with allowed CIDR:
	`java -jar ksecurevpn.jar --allowed=192.168.1.0/24 server`

Security note: defaults are intentionally conservative. Prefer binding to `127.0.0.1` or a dedicated interface and set `KSECUREVPN_ALLOWED_IPS` to explicit IPs/CIDRs in production.

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
