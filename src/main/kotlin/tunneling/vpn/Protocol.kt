package tunneling.vpn

/**
 * Minimal protocol additions to carry raw IP packets over the existing AES-encrypted stream.
 *
 * We reuse the existing framing at the transport layer (per-message AES with 16-byte IV + 4-byte length + ciphertext).
 * The plaintext inside that ciphertext starts with a single byte [FrameType] followed by type-specific payload.
 */
object FrameType {
    const val AUTH: Byte = 0x01
    const val AUTH_RESPONSE: Byte = 0x02
    const val CONTROL: Byte = 0x10
    const val PACKET: Byte = 0x11

    // v2 handshake (Noise-like) frames carried before AUTH when protocol v2 is enabled
    const val HANDSHAKE_INIT: Byte = 0x20
    const val HANDSHAKE_RESP: Byte = 0x21
    const val HANDSHAKE_FIN: Byte = 0x22
}

/** Control message kinds carried inside [FrameType.CONTROL] frames. */
object ControlKind {
    const val IP_REQUEST: Byte = 0x01 // client -> server: request virtual IP
    const val IP_ASSIGN: Byte = 0x02 // server -> client: assign virtual IP (4 bytes IPv4)
    const val KEEPALIVE: Byte = 0x03 // bidirectional keepalive
}

/** Protocol version flags (env-controlled). Default is v2 when not explicitly set to v1. */
object ProtocolVersion {
    const val V1: Int = 1
    const val V2: Int = 2
}
