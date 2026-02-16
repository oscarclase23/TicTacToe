package org.dam.project.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dam.project.network.NetworkMessage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * JVM implementation of NetworkClient using java.net.Socket.
 */
actual class NetworkClient {
    
    private var socket: Socket? = null
    private var input: BufferedReader? = null
    private var output: PrintWriter? = null
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Connects to the server with timeout.
     */
    actual suspend fun connect(host: String, port: Int): Unit = withContext(Dispatchers.IO) {
        try {
            // Close existing connection if any
            close()
            
            // Force IPv4 for localhost on Windows to avoid IPv6 connection issues
            val targetHost = if (host == "localhost") "127.0.0.1" else host
            println("[NetworkClient] Connecting to $targetHost:$port...")
            
            // Create socket & set options BEFORE connecting in case of OS defaults
            val newSocket = Socket()
            newSocket.keepAlive = true
            newSocket.tcpNoDelay = true // Disable Nagle's algorithm for low latency
            // newSocket.soTimeout = 0 // Infinite read timeout (default)
            
            // Connect with short timeout (2000ms) to fail fast
            newSocket.connect(InetSocketAddress(targetHost, port), 2000)
            
            socket = newSocket
            
            // Initialize streams immediately
            input = BufferedReader(InputStreamReader(newSocket.getInputStream()))
            output = PrintWriter(newSocket.getOutputStream(), true) // Auto-flush enabled
            
            println("[NetworkClient] Connected to $targetHost:$port")
        } catch (e: Exception) {
            println("[NetworkClient] Connect failed: ${e.message}")
            close()
            throw Exception("Failed to connect to server: ${e.message}", e)
        }
    }
    
    /**
     * Sends a message to the server.
     */
    actual suspend fun send(message: NetworkMessage): Unit = withContext(Dispatchers.IO) {
        try {
            val out = output ?: throw Exception("Not connected")
            
            val jsonString = json.encodeToString(message)
            out.println(jsonString)
            // out.flush() // Auto-flush is enabled in PrintWriter constructor
        } catch (e: Exception) {
            throw Exception("Failed to send message: ${e.message}", e)
        }
    }
    
    /**
     * Receives a message from the server.
     * Blocks until a message is received.
     */
    actual suspend fun receive(): NetworkMessage = withContext(Dispatchers.IO) {
        try {
            if (input == null) throw Exception("Not connected")
            
            val line = input?.readLine() ?: throw Exception("Connection closed by server")
            val message = json.decodeFromString<NetworkMessage>(line)
            message
        } catch (e: Exception) {
            throw Exception("Failed to receive message: ${e.message}", e)
        }
    }
    
    /**
     * Checks if connected.
     */
    actual fun isConnected(): Boolean {
        return socket?.isConnected == true && socket?.isClosed == false
    }
    
    /**
     * Closes the connection.
     */
    actual fun close() {
        try {
            input?.close()
            output?.close()
            socket?.close()
            println("[NetworkClient] Connection closed")
        } catch (e: Exception) {
            println("[NetworkClient] Error closing connection: ${e.message}")
        } finally {
            socket = null
            input = null
            output = null
        }
    }
}
