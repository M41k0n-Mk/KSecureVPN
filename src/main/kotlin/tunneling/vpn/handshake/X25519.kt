package tunneling.vpn.handshake

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement

/**
 * Minimal helpers for X25519 using JCE (Java 21+ supports XDH/X25519).
 * We use standard encodings:
 *  - Public: X.509 SubjectPublicKeyInfo (export/import via X509EncodedKeySpec)
 *  - Private: PKCS#8 (export/import via PKCS8EncodedKeySpec)
 */
object X25519 {
    private const val ALG = "X25519"

    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance(ALG)
        return kpg.generateKeyPair()
    }

    fun agree(
        privateKey: PrivateKey,
        peerPublic: PublicKey,
    ): ByteArray {
        val ka = KeyAgreement.getInstance(ALG)
        ka.init(privateKey)
        ka.doPhase(peerPublic, true)
        return ka.generateSecret()
    }

    fun exportPublicKey(pk: PublicKey): ByteArray = pk.encoded

    fun importPublicKey(x509: ByteArray): PublicKey = KeyFactory.getInstance(ALG).generatePublic(X509EncodedKeySpec(x509))

    fun exportPrivateKey(sk: PrivateKey): ByteArray = sk.encoded

    fun importPrivateKey(pkcs8: ByteArray): PrivateKey = KeyFactory.getInstance(ALG).generatePrivate(PKCS8EncodedKeySpec(pkcs8))

    fun b64(input: ByteArray): String = Base64.getEncoder().encodeToString(input)

    fun fromB64(b64: String): ByteArray = Base64.getDecoder().decode(b64)
}
