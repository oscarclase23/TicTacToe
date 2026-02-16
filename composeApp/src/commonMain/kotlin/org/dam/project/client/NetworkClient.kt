package org.dam.project.client

import org.dam.project.network.NetworkMessage

/**
 * Network client interface for socket communication.
 * Platform-specific implementations handle actual socket operations.
 */
expect class NetworkClient {
    
    /**
     * Connects to the server.
     * 
     * @param host Server hostname or IP
     * @param port Server port
     * @throws Exception if connection fails
     */
    suspend fun connect(host: String, port: Int)
    
    /**
     * Sends a message to the server.
     * 
     * @param message Network message to send
     * @throws Exception if send fails
     */
    suspend fun send(message: NetworkMessage)
    
    /**
     * Receives a message from the server.
     * This is a blocking call that waits for the next message.
     * 
     * @return Received network message
     * @throws Exception if receive fails or connection is closed
     */
    suspend fun receive(): NetworkMessage
    
    /**
     * Checks if the connection is active.
     * 
     * @return true if connected, false otherwise
     */
    fun isConnected(): Boolean
    
    /**
     * Closes the connection.
     */
    fun close()
}
