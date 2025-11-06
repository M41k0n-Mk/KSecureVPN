package auth

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

class AuthService(private val usersFile: Path = Paths.get("config", "users.properties")) {

    private val users = ConcurrentHashMap<String, UserRecord>()

    init {
        if (Files.exists(usersFile)) {
            load()
        } else {
            // ensure parent exists for later writes
            Files.createDirectories(usersFile.parent)
        }
    }

    private fun load() {
        val props = Properties()
        Files.newInputStream(usersFile).use { props.load(it) }
        for ((k, v) in props) {
            val username = k.toString()
            val parts = v.toString().split(':')
            if (parts.size != 3) continue
            val iterations = parts[0].toInt()
            val salt = PasswordHasher.fromBase64(parts[1])
            val hash = PasswordHasher.fromBase64(parts[2])
            users[username] = UserRecord(username, salt, hash, iterations)
        }
    }

    private fun persist() {
        val props = Properties()
        for ((username, record) in users) {
            val value = "${record.iterations}:${PasswordHasher.toBase64(record.salt)}:${PasswordHasher.toBase64(record.hash)}"
            props.setProperty(username, value)
        }
        Files.newOutputStream(usersFile).use { props.store(it, "KSecureVPN users (username=iterations:base64salt:base64hash)") }
    }

    fun authenticate(
        username: String,
        password: CharArray,
    ): Boolean {
        val record = users[username] ?: return false
        return PasswordHasher.verify(password, record.salt, record.iterations, record.hash)
    }

    /**
     * Add user and persist. Returns false if user already exists.
     * Use only on trusted admin/local context.
     */
    fun addUser(
        username: String,
        password: CharArray,
        iterations: Int = 100_000,
    ): Boolean {
        if (users.containsKey(username)) return false
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hash(password, salt, iterations)
        val rec = UserRecord(username, salt, hash, iterations)
        users[username] = rec
        persist()
        return true
    }
}
