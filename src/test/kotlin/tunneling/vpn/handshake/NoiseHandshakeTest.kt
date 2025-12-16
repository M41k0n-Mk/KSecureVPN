package tunneling.vpn.handshake

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator

class HkdfTest {
    @Test
    fun testExtractExpand() {
        val salt = byteArrayOf(0, 1, 2, 3)
        val ikm = byteArrayOf(10, 20, 30, 40, 50)
        val prk = Hkdf.extract(salt, ikm)
        assertEquals(32, prk.size)

        val expanded = Hkdf.expand(prk, "info".toByteArray(), 64)
        assertEquals(64, expanded.size)
    }

    @Test
    fun testSha256() {
        val data = "test".toByteArray()
        val hash = Hkdf.sha256(data)
        assertEquals(32, hash.size)
    }

    @Test
    fun testExtractWithNullSalt() {
        val ikm = byteArrayOf(1, 2, 3, 4)
        val prk = Hkdf.extract(null, ikm)
        assertEquals(32, prk.size)
    }
}

class X25519Test {
    @Test
    fun testGenerateAndExport() {
        val kp = X25519.generateKeyPair()
        assertNotNull(kp.public)
        assertNotNull(kp.private)

        val pubX509 = X25519.exportPublicKey(kp.public)
        assertTrue(pubX509.size > 0)

        val privPkcs8 = X25519.exportPrivateKey(kp.private)
        assertTrue(privPkcs8.size > 0)
    }

    @Test
    fun testKeyAgreement() {
        val kp1 = X25519.generateKeyPair()
        val kp2 = X25519.generateKeyPair()

        val secret1 = X25519.agree(kp1.private, kp2.public)
        val secret2 = X25519.agree(kp2.private, kp1.public)

        assertTrue(secret1.contentEquals(secret2), "Key agreement should produce same shared secret")
    }

    @Test
    fun testB64Encoding() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val b64 = X25519.b64(data)
        assertTrue(b64.isNotEmpty())

        val decoded = X25519.fromB64(b64)
        assertTrue(decoded.contentEquals(data))
    }

    @Test
    fun testImportExportPublicKey() {
        val kp1 = X25519.generateKeyPair()
        val exported = X25519.exportPublicKey(kp1.public)
        val imported = X25519.importPublicKey(exported)

        val reexported = X25519.exportPublicKey(imported)
        assertTrue(exported.contentEquals(reexported))
    }
}

class MessagesTest {
    @Test
    fun testEncodeDecodeInit() {
        val init =
            Messages.Init(
                clientEphemeralPubX509 = byteArrayOf(1, 2, 3, 4, 5),
                clientNonce = ByteArray(32) { it.toByte() },
            )

        val encoded = Messages.encodeInit(init)
        assertTrue(encoded.isNotEmpty())

        val decoded = Messages.decodeInit(encoded)
        assertNotNull(decoded)
        assertTrue(decoded!!.clientEphemeralPubX509.contentEquals(init.clientEphemeralPubX509))
        assertTrue(decoded.clientNonce.contentEquals(init.clientNonce))
    }

    @Test
    fun testEncodeDecodeResp() {
        val resp =
            Messages.Resp(
                serverEphemeralPubX509 = byteArrayOf(1, 2, 3),
                serverStaticPubX509 = byteArrayOf(4, 5, 6),
                serverNonce = ByteArray(32) { (it * 2).toByte() },
                serverProofHmac = ByteArray(32) { (it + 1).toByte() },
            )

        val encoded = Messages.encodeResp(resp)
        assertTrue(encoded.isNotEmpty())

        val decoded = Messages.decodeResp(encoded)
        assertNotNull(decoded)
        assertTrue(decoded!!.serverEphemeralPubX509.contentEquals(resp.serverEphemeralPubX509))
        assertTrue(decoded.serverStaticPubX509.contentEquals(resp.serverStaticPubX509))
    }

    @Test
    fun testEncodeDecodeFin() {
        val fin =
            Messages.Fin(
                clientProofHmac = ByteArray(32) { (it * 3).toByte() },
            )

        val encoded = Messages.encodeFin(fin)
        assertTrue(encoded.isNotEmpty())

        val decoded = Messages.decodeFin(encoded)
        assertNotNull(decoded)
        assertTrue(decoded!!.clientProofHmac.contentEquals(fin.clientProofHmac))
    }

    @Test
    fun testInvalidMagic() {
        val invalid = byteArrayOf(1, 2, 3, 4, 5, 0, 1)
        assertNull(Messages.decodeInit(invalid))
        assertNull(Messages.decodeResp(invalid))
        assertNull(Messages.decodeFin(invalid))
    }
}

class NoiseHandshakeTest {
    @Test
    fun testClientInitGeneration() {
        val (encoded, state) = NoiseHandshake.clientInit()

        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
        assertNotNull(state)
        assertEquals(32, state.clientNonce.size)
    }

    @Test
    fun testFullHandshakeFlow() {
        // Generate server static key pair
        val kpg = KeyPairGenerator.getInstance("X25519")
        val serverStaticKeyPair = kpg.generateKeyPair()

        // Step 1: Client init
        val (clientInitBytes, clientState) = NoiseHandshake.clientInit()
        assertNotNull(clientInitBytes)
        assertNotNull(clientState)

        // Step 2: Server responds
        val (serverRespBytes, serverState) =
            NoiseHandshake.serverRespond(
                clientInitBytes!!,
                serverStaticKeyPair.private,
                serverStaticKeyPair.public,
            )
        assertNotNull(serverRespBytes)
        assertNotNull(serverState)

        // Step 3: Client finalizes
        val (clientFinBytes, clientKeys) =
            NoiseHandshake.clientFinalize(
                serverRespBytes!!,
                clientState!!,
                serverStaticKeyPair.public,
            )
        assertNotNull(clientFinBytes)
        assertNotNull(clientKeys)

        // Step 4: Server finalizes
        val serverKeys = NoiseHandshake.serverFinalize(clientFinBytes!!, serverState!!)
        assertNotNull(serverKeys)

        // Verify both sides have same encryption key
        assertArrayEquals(
            clientKeys!!.encryptionKey.encoded,
            serverKeys!!.encryptionKey.encoded,
            "Client and server should derive same encryption key",
        )
    }

    @Test
    fun testInvalidServerPublicKey() {
        // Generate two different server key pairs
        val kpg = KeyPairGenerator.getInstance("X25519")
        val serverKeyPair1 = kpg.generateKeyPair()
        val serverKeyPair2 = kpg.generateKeyPair()

        // Step 1 & 2: Normal exchange
        val (clientInitBytes, clientState) = NoiseHandshake.clientInit()
        val (serverRespBytes, _) =
            NoiseHandshake.serverRespond(
                clientInitBytes!!,
                serverKeyPair1.private,
                serverKeyPair1.public,
            )

        // Step 3: Try to finalize with wrong server public key
        val (_, clientKeys) =
            NoiseHandshake.clientFinalize(
                serverRespBytes!!,
                clientState!!,
                serverKeyPair2.public, // Wrong key!
            )

        assertNull(clientKeys, "Should reject handshake with mismatched server key")
    }

    @Test
    fun testInvalidInitMessage() {
        val kpg = KeyPairGenerator.getInstance("X25519")
        val serverKeyPair = kpg.generateKeyPair()

        val invalidInit = byteArrayOf(1, 2, 3, 4, 5)
        val (respBytes, respState) =
            NoiseHandshake.serverRespond(
                invalidInit,
                serverKeyPair.private,
                serverKeyPair.public,
            )

        assertNull(respBytes)
        assertNull(respState)
    }
}
