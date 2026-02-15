package org.dam.project.server

import java.io.File
import java.util.Properties

/**
 * Server configuration data class.
 */
data class ServerConfig(
    val host: String,
    val port: Int,
    val maxClients: Int
)

/**
 * Loads server configuration from a properties file.
 * 
 * @param filePath Path to the server.properties file
 * @return ServerConfig with loaded or default values
 */
fun loadConfig(filePath: String): ServerConfig {
    val properties = Properties()
    val file = File(filePath)
    
    // Load properties if file exists
    if (file.exists()) {
        file.inputStream().use { input ->
            properties.load(input)
        }
        println("[ServerConfig] Loaded configuration from: $filePath")
    } else {
        println("[ServerConfig] File not found: $filePath, using defaults")
    }
    
    // Read properties with defaults
    // FORCE BIND TO 0.0.0.0 to listen on all IPv4 interfaces
    val host = properties.getProperty("server.host", "0.0.0.0")
    val port = properties.getProperty("server.port", "5678").toIntOrNull() ?: 5678
    val maxClients = properties.getProperty("max.clients", "10").toIntOrNull() ?: 10
    
    val config = ServerConfig(host, port, maxClients)
    println("[ServerConfig] Configuration: host=$host, port=$port, maxClients=$maxClients")
    
    return config
}
