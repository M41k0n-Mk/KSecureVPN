# KSecureVPN — VPN-level Tunneling (TUN/TAP) Setup

Este documento descreve como habilitar tunelamento em nível de rede usando uma interface virtual (TUN/L3). Hoje há suporte real para Linux (RealTun) e Windows (Wintun). Em sistemas sem suporte/permite, o cliente faz fallback automático para `MemoryTun` (in‑memory), garantindo que a aplicação continue operando.

Important security note:
- All traffic transported by KSecureVPN is encrypted and authenticated by AES (see `crypt/AESCipher.kt`).
- Do not log keys, plaintext traffic, or sensitive credentials. Use `SecureLogger` which avoids printing secrets.

Overview
- Client e Server trocam pacotes IPv4 encapsulados em frames criptografados.
- IPs virtuais de `10.8.0.0/24` são atribuídos aos clientes (o servidor mantém uma tabela de rotas mínima).
- Pacotes são roteados entre clientes pelo servidor. Opcionalmente, o servidor pode fazer NAT para Internet (não automatizado nesta versão).

Code structure (módulos relevantes)
- `tunneling/vpn/VirtualInterface.kt` — abstração para dispositivos TUN/TAP.
- `tunneling/vpn/Protocol.kt` — frames de controle e dados.
- `tunneling/vpn/PacketFramer.kt` — framing sobre o canal criptografado.
- `tunneling/vpn/IPv4.kt` — utilitários simples para IPv4.
- `tunneling/vpn/IpPool.kt` — alocador /24 simples (10.8.0.0/24 por padrão).
- `tunneling/vpn/RoutingTable.kt` — mapeia IPs virtuais para sessões.
- `tunneling/vpn/VpnServer.kt` — servidor experimental (auth, IP, roteamento entre clientes).
- `tunneling/vpn/VpnClient.kt` — cliente experimental que usa uma `VirtualInterface`.
- `tunneling/vpn/linux/RealTun.kt` — TUN real no Linux via `/dev/net/tun` (JNA).
- `tunneling/vpn/windows/WintunTun.kt` — TUN real no Windows via Wintun (JNA).
- `tunneling/vpn/stub/MemoryTun.kt` — TUN em memória (fallback/testes).

Linux (TUN) — quick start
1) Kernel e permissões
   - Garanta que `/dev/net/tun` existe e que seu usuário pode criar TUNs.
   - Debian/Ubuntu: `sudo apt-get install -y iproute2`.

2) Configuração de interface (exemplo manual, fora do app)
   - Servidor `tun0` com `10.8.0.1/24` e MTU:
     ```bash
     sudo ip tuntap add dev tun0 mode tun
     sudo ip addr add 10.8.0.1/24 dev tun0
     sudo ip link set dev tun0 mtu 1500 up
     ```
   - Encaminhamento IP no kernel:
     ```bash
     sudo sysctl -w net.ipv4.ip_forward=1
     ```
   - (Opcional) NAT para Internet via `eth0`:
     ```bash
     sudo iptables -t nat -A POSTROUTING -s 10.8.0.0/24 -o eth0 -j MASQUERADE
     sudo iptables -A FORWARD -s 10.8.0.0/24 -o eth0 -j ACCEPT
     sudo iptables -A FORWARD -d 10.8.0.0/24 -m state --state ESTABLISHED,RELATED -i eth0 -j ACCEPT
     ```

3) Cliente (exemplo manual)
   - `tun0` com IP atribuído (ex.: 10.8.0.2/24):
     ```bash
     sudo ip tuntap add dev tun0 mode tun
     sudo ip addr add 10.8.0.2/24 dev tun0
     sudo ip link set dev tun0 mtu 1500 up
     sudo ip route add 10.8.0.0/24 dev tun0
     ```

4) Connecting KSecureVPN
   - Start the VPN server (experimental): `VpnServer` listens on TCP port `9001`. CLI wiring is not finalized; see code.
   - Start clients using `VpnClient` and a concrete `VirtualInterface` implementation (see notes below).

VirtualInterface implementations
- Linux: `tunneling.vpn.linux.RealTun` usa `/dev/net/tun` com `IFF_TUN | IFF_NO_PI` (JNA).
- Windows: `tunneling.vpn.windows.WintunTun` usa Wintun (`wintun.dll`).
- Fallback: `tunneling.vpn.stub.MemoryTun` quando TUN real não está disponível/permitido.

Windows
- Requer `wintun.dll` (x64) disponível no PATH ou variável `KSECUREVPN_WINTUN_DLL` apontando para a DLL.
- A aplicação tenta usar `WintunTun("ksecvpn0")` automaticamente. Se falhar, faz fallback para `MemoryTun` e registra logs.

macOS
- Integração via `utun` ainda não implementada. O app usa `MemoryTun` por enquanto.

MTU e fragmentação
- MTU padrão 1500. Ajuste pode ser necessário dependendo do transporte e overhead.

Considerações de segurança
- Chaves nunca são logadas. Use variável `KSECUREVPN_KEY` com chave base64 de 32 bytes.
- Use credenciais fortes (PBKDF2 `PasswordHasher`).
- Não registre conteúdo de pacotes.

Testes
- Unitários cobrem: framing, alocação de IP, parsing IPv4, tabela de rotas e partes do servidor/cliente.
- Testes Linux/Windows para TUN real são condicionais por SO e variáveis de ambiente:
  - Linux: requer `/dev/net/tun`; smoke de I/O opcional com `ENABLE_TUN_TESTS=true`.
  - Windows: requer `wintun.dll`; smoke de I/O opcional com `ENABLE_WINTUN_TESTS=true`.
