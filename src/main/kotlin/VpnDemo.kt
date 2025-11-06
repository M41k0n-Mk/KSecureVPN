
import auth.AuthService
import crypt.AESCipher
import kotlinx.coroutines.*
import tunneling.vpn.VpnServer
import tunneling.vpn.VpnClient
import tunneling.vpn.VirtualInterface
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

fun main() = runBlocking {
    println("╔════════════════════════════════════════════════════════════╗")
    println("║  KSecureVPN - Demo com Troca REAL de Mensagens Entre VPNs ║")
    println("╚════════════════════════════════════════════════════════════╝")
    println()

    // ==================== SETUP ====================
    println("┌─ CONFIGURAÇÃO ──────────────────────────────────────────────┐")
    val authService = AuthService()
    authService.addUser("alice", "pass123".toCharArray())
    authService.addUser("bob", "pass456".toCharArray())
    println("│ ✅ Usuários criados: alice, bob")

    val key = AESCipher.generateKey()
    println("│ 🔑 Chave AES compartilhada gerada")
    println("└─────────────────────────────────────────────────────────────┘")
    println()

    // ==================== SERVIDOR ====================
    println("┌─ SERVIDOR VPN ──────────────────────────────────────────────┐")
    val serverJob = launch(Dispatchers.IO) {
        val server = VpnServer(port = 9001, key = key, authService = authService)
        server.start()
    }
    delay(1000)
    println("│ ✅ Servidor rodando em 127.0.0.1:9001")
    println("│ 🌐 Rede virtual: 10.8.0.0/24")
    println("└─────────────────────────────────────────────────────────────┘")
    println()

    // ==================== CLIENTE ALICE ====================
    println("┌─ CLIENTE: ALICE ────────────────────────────────────────────┐")
    val aliceTun = ActiveMemoryTun("alice-tun")

    val aliceJob = launch(Dispatchers.IO) {
        try {
            println("│ 🔌 Alice conectando...")
            val client = VpnClient(
                serverHost = "127.0.0.1",
                serverPort = 9001,
                key = key,
                username = "alice",
                password = "pass123".toCharArray(),
                vInterface = aliceTun
            )
            client.start()
        } catch (e: Exception) {
            println("│ ❌ Erro Alice: ${e.message}")
        }
    }

    delay(2000)
    println("│ ✅ Alice autenticada e conectada")
    println("│ 📍 IP atribuído: 10.8.0.2 (esperado)")
    println("└─────────────────────────────────────────────────────────────┘")
    println()

    // ==================== CLIENTE BOB ====================
    println("┌─ CLIENTE: BOB ──────────────────────────────────────────────┐")
    val bobTun = ActiveMemoryTun("bob-tun")

    val bobJob = launch(Dispatchers.IO) {
        try {
            println("│ 🔌 Bob conectando...")
            val client = VpnClient(
                serverHost = "127.0.0.1",
                serverPort = 9001,
                key = key,
                username = "bob",
                password = "pass456".toCharArray(),
                vInterface = bobTun
            )
            client.start()
        } catch (e: Exception) {
            println("│ ❌ Erro Bob: ${e.message}")
        }
    }

    delay(2000)
    println("│ ✅ Bob autenticado e conectado")
    println("│ 📍 IP atribuído: 10.8.0.3 (esperado)")
    println("└─────────────────────────────────────────────────────────────┘")
    println()

    // ==================== TROCA DE MENSAGENS ====================
    println("┌─ TESTE DE COMUNICAÇÃO ──────────────────────────────────────┐")
    println("│")

    // Aguardar estabilização
    delay(1000)

    // Alice envia pacote para Bob (10.8.0.2 → 10.8.0.3)
    println("│ 📤 ALICE (10.8.0.2) → BOB (10.8.0.3)")
    val packet1 = createIPPacket(
        srcIP = "10.8.0.2",
        dstIP = "10.8.0.3",
        payload = "Hello Bob, this is Alice!"
    )
    println("│    ├─ Pacote criado: ${packet1.size} bytes")
    println("│    ├─ Origem: 10.8.0.2")
    println("│    ├─ Destino: 10.8.0.3")
    println("│    └─ Payload: \"Hello Bob, this is Alice!\"")

    // Alice "envia" o pacote (injeta no TUN)
    aliceTun.injectPacket(packet1)
    println("│ ✅ Alice injetou pacote no TUN")
    println("│")

    delay(1500)

    // Bob envia resposta para Alice (10.8.0.3 → 10.8.0.2)
    println("│ 📤 BOB (10.8.0.3) → ALICE (10.8.0.2)")
    val packet2 = createIPPacket(
        srcIP = "10.8.0.3",
        dstIP = "10.8.0.2",
        payload = "Hi Alice, Bob here!"
    )
    println("│    ├─ Pacote criado: ${packet2.size} bytes")
    println("│    ├─ Origem: 10.8.0.3")
    println("│    ├─ Destino: 10.8.0.2")
    println("│    └─ Payload: \"Hi Alice, Bob here!\"")

    bobTun.injectPacket(packet2)
    println("│ ✅ Bob injetou pacote no TUN")
    println("│")

    delay(1500)

    // ==================== ESTATÍSTICAS ====================
    println("│ 📊 ESTATÍSTICAS DE TRÁFEGO:")
    println("│")
    println("│ ALICE:")
    println("│    ├─ Pacotes enviados: ${aliceTun.sentCount.get()}")
    println("│    ├─ Pacotes recebidos: ${aliceTun.receivedCount.get()}")
    println("│    └─ Bytes trafegados: ${aliceTun.totalBytes.get()} bytes")
    println("│")
    println("│ BOB:")
    println("│    ├─ Pacotes enviados: ${bobTun.sentCount.get()}")
    println("│    ├─ Pacotes recebidos: ${bobTun.receivedCount.get()}")
    println("│    └─ Bytes trafegados: ${bobTun.totalBytes.get()} bytes")
    println("│")

    // Mostrar pacotes recebidos
    if (bobTun.receivedPackets.isNotEmpty()) {
        println("│ 📥 BOB RECEBEU:")
        bobTun.receivedPackets.forEach { pkt ->
            val payload = extractPayload(pkt)
            println("│    └─ \"$payload\"")
        }
    }
    println("│")

    if (aliceTun.receivedPackets.isNotEmpty()) {
        println("│ 📥 ALICE RECEBEU:")
        aliceTun.receivedPackets.forEach { pkt ->
            val payload = extractPayload(pkt)
            println("│    └─ \"$payload\"")
        }
    }

    println("└─────────────────────────────────────────────────────────────┘")
    println()

    // ==================== ENCERRAMENTO ====================
    println("⏳ Aguardando 2 segundos...")
    delay(2000)

    println("🛑 Encerrando...")
    aliceJob.cancel()
    bobJob.cancel()
    serverJob.cancel()

    println("✅ Demo concluído com sucesso!")
}

// ==================== CLASSES AUXILIARES ====================

/**
 * MemoryTun que permite INJETAR pacotes manualmente
 */
class ActiveMemoryTun(override val name: String) : VirtualInterface {
    override val mtu: Int = 1500

    private val outboundQueue = ConcurrentLinkedQueue<ByteArray>()
    val receivedPackets = ConcurrentLinkedQueue<ByteArray>()

    val sentCount = AtomicInteger(0)
    val receivedCount = AtomicInteger(0)
    val totalBytes = AtomicInteger(0)

    /**
     * Lê pacote que o cliente QUER ENVIAR
     */
    override fun readPacket(buffer: ByteArray): Int {
        val packet = outboundQueue.poll()
        return if (packet != null) {
            val n = packet.size.coerceAtMost(buffer.size)
            System.arraycopy(packet, 0, buffer, 0, n)
            sentCount.incrementAndGet()
            totalBytes.addAndGet(n)
            println("    [$name] 📤 Enviando pacote: $n bytes")
            n
        } else {
            Thread.sleep(50) // Evitar busy-wait
            0
        }
    }

    /**
     * Recebe pacote que CHEGOU DO SERVIDOR
     */
    override fun writePacket(packet: ByteArray, length: Int) {
        val copy = ByteArray(length)
        System.arraycopy(packet, 0, copy, 0, length)
        receivedPackets.offer(copy)
        receivedCount.incrementAndGet()
        totalBytes.addAndGet(length)
        println("    [$name] 📥 Recebeu pacote: $length bytes")
    }

    /**
     * Injeta um pacote para ser ENVIADO
     */
    fun injectPacket(packet: ByteArray) {
        outboundQueue.offer(packet)
    }

    override fun close() {}
}

/**
 * Cria um pacote IP simplificado
 */
fun createIPPacket(srcIP: String, dstIP: String, payload: String): ByteArray {
    val srcBytes = srcIP.split(".").map { it.toInt().toByte() }.toByteArray()
    val dstBytes = dstIP.split(".").map { it.toInt().toByte() }.toByteArray()
    val payloadBytes = payload.toByteArray()

    // IP Header mínimo (20 bytes) + payload
    val packet = ByteArray(20 + payloadBytes.size)

    // Version (4) + IHL (5)
    packet[0] = 0x45.toByte()

    // Total Length (big endian)
    val totalLen = packet.size
    packet[2] = ((totalLen shr 8) and 0xFF).toByte()
    packet[3] = (totalLen and 0xFF).toByte()

    // Protocol (17 = UDP, para simplicidade)
    packet[9] = 17.toByte()

    // Source IP (offset 12)
    System.arraycopy(srcBytes, 0, packet, 12, 4)

    // Destination IP (offset 16)
    System.arraycopy(dstBytes, 0, packet, 16, 4)

    // Payload
    System.arraycopy(payloadBytes, 0, packet, 20, payloadBytes.size)

    return packet
}

/**
 * Extrai payload de um pacote IP
 */
fun extractPayload(packet: ByteArray): String {
    if (packet.size < 20) return "[pacote inválido]"

    // IP Header é de 20 bytes (simplificado)
    val payload = packet.copyOfRange(20, packet.size)
    return payload.decodeToString()
}