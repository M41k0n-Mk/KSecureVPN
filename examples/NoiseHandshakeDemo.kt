package examples

import tunneling.vpn.PacketFramer
import tunneling.vpn.FrameType
import tunneling.vpn.handshake.NoiseHandshake
import tunneling.vpn.AntiReplayWindow
import crypt.GcmCipher
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator

/**
 * Demo: Noise handshake + AEAD encryption + anti-replay for KSecureVPN v2.
 *
 * Shows how to:
 * 1. Perform Noise_25519_AESGCM_SHA256 handshake between client and server
 * 2. Derive shared encryption key with PFS (Perfect Forward Secrecy)
 * 3. Encrypt/decrypt frames using AES-GCM with sequence numbers
 * 4. Validate replay attacks with anti-replay window
 */
suspend fun main() {
    println("=== KSecureVPN v2 Handshake Demo ===\n")

    // Step 1: Generate server static key pair
    val kpg = KeyPairGenerator.getInstance("X25519")
    val serverStaticKeyPair = kpg.generateKeyPair()
    println("✓ Server static key pair generated")

    // Step 2: Client initiates handshake
    val (clientInitBytes, clientState) = NoiseHandshake.clientInit()
    println("✓ Client Init message created (${clientInitBytes.size} bytes)")

    // Step 3: Server responds
    val (serverRespBytes, serverState) = NoiseHandshake.serverRespond(
        clientInitBytes!!,
        serverStaticKeyPair.private,
        serverStaticKeyPair.public,
    )
    println("✓ Server Resp message created (${serverRespBytes?.size} bytes)")

    // Step 4: Client finalizes handshake
    val (clientFinBytes, clientKeys) = NoiseHandshake.clientFinalize(
        serverRespBytes!!,
        clientState!!,
        serverStaticKeyPair.public,
    )
    println("✓ Client Fin message created (${clientFinBytes?.size} bytes)")

    // Step 5: Server finalizes
    val serverKeys = NoiseHandshake.serverFinalize(clientFinBytes!!, serverState!!)
    println("✓ Server finalized handshake")

    // Verify both sides have same key
    require(
        clientKeys!!.encryptionKey.encoded.contentEquals(
            serverKeys!!.encryptionKey.encoded
        )
    )
    println("✓ Shared encryption key derived (PFS enabled)\n")

    // Step 6: Exchange encrypted frames with sequence numbers
    println("=== Data Exchange Phase ===\n")

    val clientOut = ByteArrayOutputStream()
    val payload = "Hello from Client!".toByteArray()

    // Client sends frame
    PacketFramer.sendFrameWithSeq(
        clientOut,
        clientKeys.encryptionKey,
        FrameType.PACKET,
        seq = 1L,
        payload,
    )
    println("✓ Client sent frame with seq=1")

    // Server receives and validates
    val serverIn = ByteArrayInputStream(clientOut.toByteArray())
    val antiReplay = AntiReplayWindow()

    val frame = PacketFramer.readFrameWithSeq(serverIn, serverKeys.encryptionKey)
    require(frame != null)
    require(frame.type == FrameType.PACKET)
    require(frame.payload.contentEquals(payload))
    require(antiReplay.accept(frame.seq))
    println("✓ Server decrypted frame, verified MAC, seq=${frame.seq} (anti-replay OK)\n")

    // Step 7: Demonstrate replay protection
    println("=== Replay Attack Demonstration ===\n")

    // Try to replay the same frame
    val serverIn2 = ByteArrayInputStream(clientOut.toByteArray())
    val frame2 = PacketFramer.readFrameWithSeq(serverIn2, serverKeys.encryptionKey)
    require(frame2 != null)
    val accepted = antiReplay.accept(frame2.seq)
    require(!accepted) { "Replay detection failed!" }
    println("✓ Replay attack blocked (seq=${frame2.seq} already seen)")

    // Try with future sequence number
    val clientOut3 = ByteArrayOutputStream()
    PacketFramer.sendFrameWithSeq(
        clientOut3,
        clientKeys.encryptionKey,
        FrameType.PACKET,
        seq = 2L,
        "Second message".toByteArray(),
    )
    val serverIn3 = ByteArrayInputStream(clientOut3.toByteArray())
    val frame3 = PacketFramer.readFrameWithSeq(serverIn3, serverKeys.encryptionKey)
    require(frame3 != null)
    require(antiReplay.accept(frame3.seq))
    println("✓ Valid future sequence accepted (seq=${frame3.seq})\n")

    println("=== Security Features Active ===")
    println("✓ AEAD encryption (AES-256-GCM)")
    println("✓ Perfect Forward Secrecy (Noise handshake)")
    println("✓ Mutual authentication (server static + ephemeral)")
    println("✓ Anti-replay window (64-position bitmap)")
    println("✓ Sequence numbering per frame")
}
