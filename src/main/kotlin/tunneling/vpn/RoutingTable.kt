package tunneling.vpn

import java.util.concurrent.ConcurrentHashMap

/** Maps destination IPv4 (as Int) to a session sender function. */
class RoutingTable {
    data class Entry(
        val ipInt: Int,
        val sendPacket: (ByteArray, Int) -> Unit,
    )

    private val routes = ConcurrentHashMap<Int, Entry>()

    fun add(ipInt: Int, sender: (ByteArray, Int) -> Unit) {
        routes[ipInt] = Entry(ipInt, sender)
    }

    fun remove(ipInt: Int) {
        routes.remove(ipInt)
    }

    fun lookup(ipInt: Int): Entry? = routes[ipInt]
}
