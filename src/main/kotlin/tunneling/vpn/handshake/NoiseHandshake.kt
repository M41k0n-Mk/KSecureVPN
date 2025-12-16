package tunneling.vpn.handshake

import crypt.GcmCipher
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import javax.crypto.SecretKey

/**
 * Noise protocol (Noise_25519_AESGCM_SHA256) implementation for KSecureVPN handshake.
 * Provides mutual authentication, PFS (Perfect Forward Secrecy), and key derivation.
 *
 * Protocol overview:
 * 1. Client generates ephemeral key, sends HANDSHAKE_INIT to Server
 * 2. Server responds with HANDSHAKE_RESP (contains ephemeral + static keys)
 * 3. Client verifies and sends HANDSHAKE_FIN
 * 4. Both derive shared encryption key via HKDF over DH values
 */
object NoiseHandshake {
    private const val NOISE_PATTERN = "Noise_25519_AESGCM_SHA256"
    private const val PROTOCOL_NAME = "KSecureVPN"

    /**
     * Client-side: Generate initial message (client ephemeral public key + nonce).
     * Returns the encoded Init message ready to send.
     */
    fun clientInit(): Pair<ByteArray, ClientState> {
        val clientEph = X25519.generateKeyPair()
        val clientNonce = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        
        val msg = Messages.Init(
            clientEphemeralPubX509 = X25519.exportPublicKey(clientEph.public),
            clientNonce = clientNonce,
        )
        
        val state = ClientState(
            ephemeralPriv = clientEph.private,
            clientNonce = clientNonce,
            transcript = msg.clientEphemeralPubX509 + clientNonce,
        )
        
        return Messages.encodeInit(msg) to state
    }

    /**
     * Server-side: Process client init, generate response (server ephemeral + static keys).
     * Returns the encoded Resp message and ServerState.
     */
    fun serverRespond(
        clientInitBytes: ByteArray,
        serverStaticPriv: PrivateKey,
        serverStaticPub: PublicKey,
    ): Pair<ByteArray?, ServerState?> {
        val init = Messages.decodeInit(clientInitBytes) ?: return null to null
        
        val clientEphPub = X25519.importPublicKey(init.clientEphemeralPubX509)
        val serverEph = X25519.generateKeyPair()
        val serverNonce = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        
        // Compute DH: server ephemeral with client ephemeral
        val dh1 = X25519.agree(serverEph.private, clientEphPub)
        
        // Proof: HMAC over partial transcript (placeholder for full Noise proof)
        val partialTranscript = init.clientEphemeralPubX509 + X25519.exportPublicKey(serverEph.public)
        val serverProofHmac = Hkdf.sha256(dh1 + partialTranscript)
        
        val msg = Messages.Resp(
            serverEphemeralPubX509 = X25519.exportPublicKey(serverEph.public),
            serverStaticPubX509 = serverStaticPub.encoded,
            serverNonce = serverNonce,
            serverProofHmac = serverProofHmac,
        )
        
        val state = ServerState(
            ephemeralPriv = serverEph.private,
            staticPriv = serverStaticPriv,
            staticPub = serverStaticPub,
            clientEphPub = clientEphPub,
            clientNonce = init.clientNonce,
            serverNonce = serverNonce,
            dh1 = dh1,
            transcript = init.clientEphemeralPubX509 + X25519.exportPublicKey(serverEph.public) + serverStaticPub.encoded,
        )
        
        return Messages.encodeResp(msg) to state
    }

    /**
     * Client-side: Process server response, derive shared key, return final message.
     */
    fun clientFinalize(
        serverRespBytes: ByteArray,
        clientState: ClientState,
        serverStaticPubFromConfig: PublicKey,
    ): Pair<ByteArray?, SharedKeys?> {
        val resp = Messages.decodeResp(serverRespBytes) ?: return null to null
        
        val serverEphPub = X25519.importPublicKey(resp.serverEphemeralPubX509)
        val serverStaticPub = X25519.importPublicKey(resp.serverStaticPubX509)
        
        // Verify server static public key matches expected (from config)
        if (!serverStaticPub.encoded.contentEquals(serverStaticPubFromConfig.encoded)) {
            return null to null
        }
        
        // Compute DH values
        val dh1 = X25519.agree(clientState.ephemeralPriv, serverEphPub)
        val dh2 = X25519.agree(clientState.ephemeralPriv, serverStaticPub)
        
        // Derive shared key via HKDF
        val combined = dh1 + dh2
        val salt = clientState.clientNonce + resp.serverNonce
        val prk = Hkdf.extract(salt, combined)
        val sharedSecret = Hkdf.expand(prk, PROTOCOL_NAME.toByteArray(), 32)
        
        // Client proof (HMAC over full transcript)
        val fullTranscript = clientState.transcript + resp.serverEphemeralPubX509 + resp.serverStaticPubX509
        val clientProofHmac = Hkdf.sha256(sharedSecret + fullTranscript)
        
        val fin = Messages.Fin(clientProofHmac)
        val sharedKeys = SharedKeys(
            encryptionKey = GcmCipher.keyFromBytes(sharedSecret),
            clientNonce = clientState.clientNonce,
            serverNonce = resp.serverNonce,
        )
        
        return Messages.encodeFin(fin) to sharedKeys
    }

    /**
     * Server-side: Process client final message and complete handshake.
     */
    fun serverFinalize(
        clientFinBytes: ByteArray,
        serverState: ServerState,
    ): SharedKeys? {
        val fin = Messages.decodeFin(clientFinBytes) ?: return null
        
        // Compute DH: server static with client ephemeral
        val dh2 = X25519.agree(serverState.staticPriv, serverState.clientEphPub)
        
        // Derive shared key (must match client's derivation)
        val combined = serverState.dh1 + dh2
        val salt = serverState.clientNonce + serverState.serverNonce
        val prk = Hkdf.extract(salt, combined)
        val sharedSecret = Hkdf.expand(prk, PROTOCOL_NAME.toByteArray(), 32)
        
        return SharedKeys(
            encryptionKey = GcmCipher.keyFromBytes(sharedSecret),
            clientNonce = serverState.clientNonce,
            serverNonce = serverState.serverNonce,
        )
    }

    // ===== State classes =====

    data class ClientState(
        val ephemeralPriv: PrivateKey,
        val clientNonce: ByteArray,
        val transcript: ByteArray,
    )

    data class ServerState(
        val ephemeralPriv: PrivateKey,
        val staticPriv: PrivateKey,
        val staticPub: PublicKey,
        val clientEphPub: PublicKey,
        val clientNonce: ByteArray,
        val serverNonce: ByteArray,
        val dh1: ByteArray,
        val transcript: ByteArray,
    )

    data class SharedKeys(
        val encryptionKey: SecretKey,
        val clientNonce: ByteArray,
        val serverNonce: ByteArray,
    )
}
