package config

import java.io.FileInputStream
import java.net.InetAddress
import java.util.Properties

/**
 * Configuration for server binding and client connection settings.
 */
data class Config(
    val bindAddress: String = "127.0.0.1",
    val port: Int = 9000,
    val allowedCidrs: List<String> = emptyList(),
) {
    /**
     * Server configuration with validated binding.
     */
    val serverConfig: ServerConfig by lazy { 
        ServerConfig(bindAddress, port, allowedCidrs) 
    }
    
    /**
     * Client configuration for connecting to server.
     */
    val clientConfig: ClientConfig by lazy { 
        ClientConfig(bindAddress, port) 
    }
}

data class ServerConfig(
    val bindAddress: String,
    val port: Int, 
    val allowedCidrs: List<String>
)

data class ClientConfig(
    val targetHost: String,
    val targetPort: Int
)

/**
 * Idiomatic configuration loader with clean precedence handling.
 */
object ConfigLoader {
    /**
     * Load configuration from (precedence): CLI map → env vars → config file → defaults.
     */
    fun load(cliArgs: Map<String, String> = emptyMap()): Config {
        val configSources = ConfigSources.from(cliArgs)
        
        return Config(
            bindAddress = configSources.getValidatedBindAddress(),
            port = configSources.getValidatedPort(),
            allowedCidrs = configSources.getParsedAllowedCidrs()
        )
    }
}

/**
 * Encapsulates all configuration sources with precedence handling.
 */
private class ConfigSources(
    private val cli: Map<String, String>,
    private val env: Map<String, String>,
    private val fileProps: Properties
) {
    companion object {
        private const val DEFAULT_BIND = "127.0.0.1"
        private const val DEFAULT_PORT = 9000
        
        fun from(cliArgs: Map<String, String>): ConfigSources {
            val env = System.getenv()
            val fileProps = loadConfigFile(cliArgs, env)
            return ConfigSources(cliArgs, env, fileProps)
        }
        
        private fun loadConfigFile(cli: Map<String, String>, env: Map<String, String>): Properties {
            val configPath = cli["config"] ?: env["KSECUREVPN_CONFIG"]
            return configPath?.let { path ->
                runCatching { 
                    Properties().apply { 
                        FileInputStream(path).use(::load) 
                    } 
                }.getOrElse { Properties() }
            } ?: Properties()
        }
    }
    
    private fun lookup(key: String): String? = 
        cli[key] ?: env[key] ?: env["KSECUREVPN_${key.uppercase()}"] ?: fileProps.getProperty(key)
    
    fun getValidatedBindAddress(): String {
        val bind = lookup("bind") ?: DEFAULT_BIND
        return if (bind.isValidInetAddress()) bind else DEFAULT_BIND
    }
    
    fun getValidatedPort(): Int = 
        lookup("port")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: DEFAULT_PORT
    
    fun getParsedAllowedCidrs(): List<String> {
        val allowedRaw = lookup("allowed") ?: ""
        return allowedRaw.splitToSequence(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
    }
    
    private fun String.isValidInetAddress(): Boolean = 
        runCatching { InetAddress.getByName(this) }.isSuccess
}
