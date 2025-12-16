package tunneling.vpn.linux

import logging.LogLevel
import logging.SecureLogger

/**
 * Utilitários para configurar rede no Linux para atuação do servidor como gateway/NAT.
 * NOTA: requer privilégios adequados (root/cap_net_admin) para efetivar mudanças.
 */
object SystemNetworking {
    private val logger = SecureLogger.getInstance()

    /**
     * Configura a interface TUN, IP forwarding e NAT (iptables) quando possível.
     * @param tunName nome da interface TUN (ex.: ksecvpn0)
     * @param cidr endereço/máscara do gateway da VPN (ex.: 10.8.0.1/24)
     * @param mtu MTU para a interface
     * @param wanIf nome da interface de saída (WAN) para MASQUERADE. Se null/vazio, NAT é pulado.
     */
    fun configureLinuxForVpn(
        tunName: String,
        cidr: String,
        mtu: Int,
        wanIf: String?,
    ) {
        if (!isLinux()) {
            logger.logSessionEvent("net", LogLevel.INFO, "System networking auto-config skipped (not Linux)")
            return
        }

        // Sobe a interface e define IP/MTU
        runSafe(listOf("ip", "link", "set", "dev", tunName, "up"), "bring up TUN $tunName")
        runSafe(listOf("ip", "addr", "flush", "dev", tunName), "flush addresses on $tunName")
        runSafe(listOf("ip", "addr", "add", cidr, "dev", tunName), "assign $cidr to $tunName")
        runSafe(listOf("ip", "link", "set", "dev", tunName, "mtu", mtu.toString()), "set MTU $mtu on $tunName")

        // Habilita IP forwarding
        runSafe(listOf("sysctl", "-w", "net.ipv4.ip_forward=1"), "enable ip_forward")

        // NAT/MASQUERADE + abertura de porta no firewall (opcional)
        val wan = wanIf?.trim().orEmpty()
        val firewallBackend = (System.getenv("KSECUREVPN_FIREWALL_BACKEND") ?: "").lowercase()
        val openPort = System.getenv("KSECUREVPN_FIREWALL_OPEN_PORT")?.toBoolean() ?: true
        val permanent = System.getenv("KSECUREVPN_FIREWALL_PERMANENT")?.toBoolean() ?: false

        if (wan.isNotEmpty()) {
            val subnet = cidrToSubnet(cidr)
            val backend = chooseNatBackend(firewallBackend)
            logger.logSessionEvent("net", LogLevel.INFO, "Config NAT backend: $backend (WAN=$wan, subnet=$subnet)")
            when (backend) {
                "nftables" -> applyNftablesNatAndForward(subnet, tunName, wan)
                else -> applyIptablesNatAndForward(subnet, tunName, wan)
            }
        } else {
            logger.logSessionEvent("net", LogLevel.WARN, "KSECUREVPN_WAN_IFACE not set; skipping NAT configuration")
        }

        if (openPort) {
            openFirewallUdpPort(9001, permanent)
        } else {
            logger.logSessionEvent("net", LogLevel.INFO, "Firewall port open disabled by env")
        }
    }

    private fun chooseNatBackend(requested: String): String {
        return when (requested) {
            "nft", "nftables" -> "nftables"
            "iptables" -> "iptables"
            else -> if (hasCmd("nft")) "nftables" else "iptables"
        }
    }

    private fun applyIptablesNatAndForward(subnet: String, tunName: String, wan: String) {
        // NAT masquerade
        runSafe(listOf("iptables", "-t", "nat", "-C", "POSTROUTING", "-s", subnet, "-o", wan, "-j", "MASQUERADE"), "check NAT rule", true)
        runSafe(listOf("iptables", "-t", "nat", "-A", "POSTROUTING", "-s", subnet, "-o", wan, "-j", "MASQUERADE"), "add NAT masquerade", true)

        // Forwarding rules
        runSafe(listOf("iptables", "-C", "FORWARD", "-i", tunName, "-o", wan, "-j", "ACCEPT"), "check forward out", true)
        runSafe(listOf("iptables", "-A", "FORWARD", "-i", tunName, "-o", wan, "-j", "ACCEPT"), "add forward out", true)

        runSafe(listOf("iptables", "-C", "FORWARD", "-i", wan, "-o", tunName, "-m", "state", "--state", "ESTABLISHED,RELATED", "-j", "ACCEPT"), "check forward in established", true)
        runSafe(listOf("iptables", "-A", "FORWARD", "-i", wan, "-o", tunName, "-m", "state", "--state", "ESTABLISHED,RELATED", "-j", "ACCEPT"), "add forward in established", true)
    }

    private fun applyNftablesNatAndForward(subnet: String, tunName: String, wan: String) {
        // Cria tabela/chain NAT se necessário e adiciona regra de masquerade
        runSafe(listOf("nft", "add", "table", "ip", "nat"), "create nft nat table", true)
        runSafe(listOf("nft", "add", "chain", "ip", "nat", "POSTROUTING", "{", "type", "nat", "hook", "postrouting", "priority", "100;", "}") , "create nft POSTROUTING", true)
        runSafe(listOf("nft", "add", "rule", "ip", "nat", "POSTROUTING", "oifname", wan, "ip", "saddr", subnet, "masquerade"), "add nft masquerade", true)

        // Regras de forward no filtro
        runSafe(listOf("nft", "add", "table", "ip", "filter"), "create nft filter table", true)
        runSafe(listOf("nft", "add", "chain", "ip", "filter", "FORWARD", "{", "type", "filter", "hook", "forward", "priority", "0;", "}"), "create nft FORWARD chain", true)
        runSafe(listOf("nft", "add", "rule", "ip", "filter", "FORWARD", "iifname", tunName, "oifname", wan, "accept"), "nft forward out", true)
        runSafe(listOf("nft", "add", "rule", "ip", "filter", "FORWARD", "iifname", wan, "oifname", tunName, "ct", "state", "related,established", "accept"), "nft forward in established", true)
    }

    private fun openFirewallUdpPort(port: Int, permanent: Boolean) {
        // ufw (Ubuntu/Debian) – não fatal quando não instalado
        runSafe(listOf("ufw", "allow", "$port/udp"), "ufw allow $port/udp", true)
        // firewalld (CentOS/Fedora/RHEL)
        val permFlag = if (permanent) "--permanent" else ""
        if (permFlag.isNotEmpty()) {
            runSafe(listOf("bash", "-lc", "firewall-cmd $permFlag --add-port=${port}/udp"), "firewalld add-port permanent", true)
            runSafe(listOf("firewall-cmd", "--reload"), "firewalld reload", true)
        } else {
            runSafe(listOf("firewall-cmd", "--add-port=${port}/udp"), "firewalld add-port runtime", true)
        }
        // iptables como fallback (abre INPUT udp:9001). Não idempotente perfeito; tentamos check + add
        runSafe(listOf("iptables", "-C", "INPUT", "-p", "udp", "--dport", port.toString(), "-j", "ACCEPT"), "iptables check INPUT $port/udp", true)
        runSafe(listOf("iptables", "-A", "INPUT", "-p", "udp", "--dport", port.toString(), "-j", "ACCEPT"), "iptables add INPUT $port/udp", true)
    }

    private fun hasCmd(cmd: String): Boolean {
        return try {
            val p = ProcessBuilder(cmd, "--version").redirectErrorStream(true).start()
            p.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun runSafe(cmd: List<String>, actionDesc: String, continueOnFail: Boolean = false) {
        try {
            val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            val exit = proc.waitFor()
            if (exit != 0) {
                val msg = "$actionDesc failed (exit=$exit): ${out.trim()}"
                if (continueOnFail) {
                    logger.logSessionEvent("net", LogLevel.WARN, msg)
                } else {
                    throw IllegalStateException(msg)
                }
            } else {
                if (out.isNotBlank()) {
                    logger.logSessionEvent("net", LogLevel.DEBUG, "$actionDesc ok: ${out.trim()}")
                } else {
                    logger.logSessionEvent("net", LogLevel.INFO, "$actionDesc ok")
                }
            }
        } catch (e: Exception) {
            val msg = "$actionDesc exception: ${e.message}"
            if (continueOnFail) {
                logger.logSessionEvent("net", LogLevel.WARN, msg)
            } else {
                throw e
            }
        }
    }

    private fun isLinux(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("linux") == true

    private fun cidrToSubnet(cidr: String): String {
        // recebe algo como 10.8.0.1/24 e retorna 10.8.0.0/24
        val parts = cidr.split('/')
        if (parts.size != 2) return cidr
        val ip = parts[0].split('.').mapNotNull { it.toIntOrNull() }
        val maskBits = parts[1].toIntOrNull() ?: return cidr
        if (ip.size != 4 || maskBits !in 0..32) return cidr
        val ipInt = (ip[0] shl 24) or (ip[1] shl 16) or (ip[2] shl 8) or ip[3]
        val mask = if (maskBits == 0) 0 else -0x1 shl (32 - maskBits)
        val net = ipInt and mask
        val a = (net ushr 24) and 0xFF
        val b = (net ushr 16) and 0xFF
        val c = (net ushr 8) and 0xFF
        val d = net and 0xFF
        return "$a.$b.$c.$d/$maskBits"
    }
}
