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

        // NAT/MASQUERADE
        val wan = wanIf?.trim().orEmpty()
        if (wan.isNotEmpty()) {
            runSafe(
                listOf("iptables", "-t", "nat", "-C", "POSTROUTING", "-s", cidrToSubnet(cidr), "-o", wan, "-j", "MASQUERADE"),
                "check NAT rule",
                continueOnFail = true,
            )
            runSafe(
                listOf("iptables", "-t", "nat", "-A", "POSTROUTING", "-s", cidrToSubnet(cidr), "-o", wan, "-j", "MASQUERADE"),
                "add NAT masquerade",
                continueOnFail = true,
            )
            // Forwarding rules
            runSafe(listOf("iptables", "-C", "FORWARD", "-i", tunName, "-o", wan, "-j", "ACCEPT"), "check forward out", true)
            runSafe(listOf("iptables", "-A", "FORWARD", "-i", tunName, "-o", wan, "-j", "ACCEPT"), "add forward out", true)

            runSafe(
                listOf("iptables", "-C", "FORWARD", "-i", wan, "-o", tunName, "-m", "state", "--state", "ESTABLISHED,RELATED", "-j", " ACCEPT"),
                "check forward in established",
                true,
            )
            runSafe(
                listOf("iptables", "-A", "FORWARD", "-i", wan, "-o", tunName, "-m", "state", "--state", "ESTABLISHED,RELATED", "-j", "ACCEPT"),
                "add forward in established",
                true,
            )
        } else {
            logger.logSessionEvent("net", LogLevel.WARN, "KSECUREVPN_WAN_IFACE not set; skipping NAT configuration")
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
