# KSecureVPN - Usage Guide & Development Roadmap

## What KSecureVPN Does Today ✅

KSecureVPN é um protótipo funcional de VPN que cria uma rede sobreposta (overlay) criptografada entre clientes através de um servidor central. A implementação atual utiliza UDP como transporte e AES‑GCM (AEAD) para confidencialidade e integridade, com números de sequência por quadro e janela anti‑replay. Em Linux, o servidor pode atuar como gateway para a Internet (egresso) quando ativado o IP forwarding e o NAT (iptables/nftables) — a automação dessas regras está embutida.

### Capacidades atuais

**✅ Criptografia autenticada (AEAD)**: Tráfego protegido com AES‑GCM + tag de autenticação
**✅ Anti‑replay**: Número de sequência por frame e janela deslizante
**✅ Transporte UDP**: Baixa latência e melhor desempenho em redes com perda
**✅ Rede P2P via servidor**: Encaminhamento de pacotes IP entre clientes
**✅ Atribuição automática de IP**: Pool 10.8.0.0/24 (10.8.0.1 reservado ao gateway)
**✅ Autenticação**: Usuário/senha com PBKDF2
**✅ TUN real**: Linux (`RealTun`) e Windows (`Wintun`) com fallback em memória
**✅ Servidor como gateway (Linux)**: IP forwarding + NAT (iptables ou nftables) automatizáveis
**✅ Cliente Linux auto‑config**: Sobe TUN, IP/MTU, rota default (opcional) e DNS

### Como usar agora

#### Quick Start (Linux)

1. **Generate encryption key**:
```bash
export KSECUREVPN_KEY=$(head -c 32 /dev/urandom | base64)
echo "Key: $KSECUREVPN_KEY"
```

2. **Start the server** (Linux; requer root/CAP_NET_ADMIN para configurar rede):
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

3. **Connect clients** (em terminais diferentes):
```bash
# Cliente 1 (recebe 10.8.0.2). Opcional: definir rota default e DNS no cliente Linux
export KSECUREVPN_CLIENT_SET_DEFAULT_ROUTE=true
export KSECUREVPN_CLIENT_DNS=8.8.8.8,8.8.4.4
KSECUREVPN_KEY=$KSECUREVPN_KEY mvn -q exec:java -Dexec.args="client" &

# Cliente 2 (recebe 10.8.0.3)
KSECUREVPN_KEY=$KSECUREVPN_KEY mvn -q exec:java -Dexec.args="client" &
```

#### Demo Mode

Run the built-in demonstration:
```bash
mvn exec:java -Dexec.args="vpn-demo"
```

This shows packet exchange between simulated clients Alice and Bob.

## Como a comunicação funciona

### Fluxo de rede
```
Client A (10.8.0.2) ────[Encrypted UDP/AEAD]──── Server ────[Encrypted UDP/AEAD]──── Client B (10.8.0.3)
        │                                               │
        └─────────────── Virtual Network ───────────────┘
```

### What Happens When You Connect

1. **Client connects** → UDP para server:9001
2. **Authentication** → Sends username/password
3. **IP Assignment** → Server gives unique IP from 10.8.0.0/24
4. **Route Registration** → Server adds client to routing table
5. **Ready** → Client can now send/receive packets to other clients

### Packet Routing

When Client A sends a packet to Client B's IP:
1. Client A encapsulates IP packet in encrypted frame
2. Envia ao servidor via UDP
3. Server decrypts, reads destination IP
4. Looks up route in routing table
5. Forwards encrypted packet to Client B
6. Client B decrypts and receives the packet

## O que você pode fazer hoje

### ✅ Testar comunicação entre pares
- Connect multiple clients to same server
- Send packets between clients using their VPN IPs
- All traffic is encrypted end-to-end

### ✅ Aprender internals de VPN
- Study the protocol implementation
- Understand packet routing and encryption
- Experiment with network programming

### ✅ Usar como overlay seguro
- Create private networks for specific applications
- Secure communication between devices

## Roadmap (atualizado)

### 🟢 Internet via VPN (Linux)
Disponível quando o servidor Linux está com `KSECUREVPN_WAN_IFACE` definido. O servidor sobe TUN, habilita `net.ipv4.ip_forward=1` e aplica NAT/FORWARD via iptables ou nftables. O cliente Linux pode definir rota default via VPN e DNS.

### 🟢 Automação do cliente (Linux)
Após `IP_ASSIGN`, o cliente configura IP/MTU, rota default (opcional) e DNS. Windows/macOS: pendente.

### 🟡 Integração de rede real (cross‑platform)
Hoje:
- Linux: TUN real `/dev/net/tun` (`tunneling.vpn.linux.RealTun`).
- Windows: Wintun (`tunneling.vpn.windows.WintunTun`) com `wintun.dll`.
- Outros (ex.: macOS): sem TUN real — usa `MemoryTun`.

Próximos passos:
- Implementar utun (macOS) e automação equivalente.

## Development Roadmap

### Phase 1: TUN real (atualizado)
- Linux: CONCLUÍDO — `/dev/net/tun` via JNA (`RealTun`).
- Windows: CONCLUÍDO — Wintun via JNA (`WintunTun`).
- macOS: PENDENTE — utun.

### Phase 2: Acesso à Internet (Linux)
- CONCLUÍDO — iptables/nftables NAT + IP forwarding + FORWARD rules via `SystemNetworking`.

### Phase 3: Automação do Cliente
- Linux: CONCLUÍDO — IP/MTU/rota default (opcional) e DNS.
- Windows/macOS: PENDENTE.

### Phase 4: Segurança de Produção
- Em progresso: transporte com AEAD (AES‑GCM) + anti‑replay (concluído).
- Próximo: PFS (Noise/TLS 1.3), rotação de chaves, limitação de taxa e anti‑DoS.

### Phase 5: Recursos Avançados
- Multi‑server/HA, métricas, reconexão/keepalive, GUI.

## Testes

### Testes automatizados
```bash
# Run all tests
mvn test

# Run with linting
mvn test ktlint:check

# Run E2E tests (GitHub Actions)
# Tests server startup and client connection
```

Observações de testes TUN:
- Testes Linux e Windows que tocam TUN real são condicionais:
  - Linux: executados apenas quando `/dev/net/tun` existe e permissões permitem. O smoke de I/O requer `ENABLE_TUN_TESTS=true`.
  - Windows: executados apenas quando `wintun.dll` está disponível. O smoke de I/O requer `ENABLE_WINTUN_TESTS=true`.

### Teste manual (smoke)
```bash
# 1) Servidor (Linux)
export KSECUREVPN_KEY=$(head -c 32 /dev/urandom | base64)
export KSECUREVPN_WAN_IFACE=eth0
mvn -q exec:java -Dexec.args="server"

# 2) Cliente (Linux)
export KSECUREVPN_KEY=... # mesma chave
export KSECUREVPN_CLIENT_SET_DEFAULT_ROUTE=true
export KSECUREVPN_CLIENT_DNS=8.8.8.8,8.8.4.4
mvn -q exec:java -Dexec.args="client"

# 3) Verifique IP público visto pelo cliente
curl -4 https://ifconfig.co    # deve exibir o IP do servidor
```

## Visão de arquitetura

### Componentes principais
- **VpnServer**: Manages connections, authentication, routing
- **VpnClient**: Connects to server, handles virtual networking
- **Protocol**: Custom frame-based communication protocol
- **RoutingTable**: Server-side packet forwarding
- **VirtualInterface**: Abstract TUN device interface

### Modelo de segurança
- **Criptografia**: AES‑GCM (AEAD) com nonce de 12B e tag de 16B
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

KSecureVPN gives you a working foundation for understanding VPN internals and creating secure peer-to-peer networks. It's perfect for learning, experimentation, and building custom networking solutions. With additional development, it can become a full-featured VPN for internet access.