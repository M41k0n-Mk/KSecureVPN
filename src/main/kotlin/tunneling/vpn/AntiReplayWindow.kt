package tunneling.vpn

/**
 * Janela anti-replay de 64 posições, baseada em seq numérico crescente (unsigned-like Long).
 */
class AntiReplayWindow {
    private var highest: Long = 0L
    private var bitmap: Long = 0L

    @Synchronized
    fun accept(seq: Long): Boolean {
        if (seq <= 0) return false
        if (seq > highest) {
            val diff = (seq - highest)
            when {
                highest == 0L -> {
                    highest = seq
                    bitmap = 1L
                }
                diff >= 64 -> {
                    highest = seq
                    bitmap = 1L
                }
                else -> {
                    bitmap = (bitmap shl diff.toInt()) or 1L
                    highest = seq
                }
            }
            return true
        }

        val back = (highest - seq)
        if (back >= 64) return false
        val mask = 1L shl back.toInt()
        val seen = (bitmap and mask) != 0L
        if (seen) return false
        bitmap = bitmap or mask
        return true
    }
}
