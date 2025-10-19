package tunneling

/**
 * Response codes used in client-server communication
 */
object ResponseCode {
    const val AUTH_FAILED: Byte = 0
    const val AUTH_SUCCESS: Byte = 1
    const val AUTH_MALFORMED: Byte = 2
    const val MSG_RECEIVED: Byte = 3
}