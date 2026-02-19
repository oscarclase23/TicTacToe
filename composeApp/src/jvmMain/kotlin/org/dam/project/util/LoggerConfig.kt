package org.dam.project.util

import java.io.FileOutputStream
import java.io.PrintStream
import java.io.OutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Configuration utility for file-based logging.
 * Redirects System.out and System.err to both console and file.
 */
object LoggerConfig {
    
    /**
     * Sets up logging to the specified file.
     * @param fileName The name of the log file (e.g., "server.log")
     */
    fun setupLogging(fileName: String) {
        try {
            val logFile = File(fileName)
            
            // Create or append to log file
            val fileOut = FileOutputStream(logFile, false) // false = overwrite on restart, true = append
            
            // Create multi-streams that write to both console and file
            val multiOut = MultiOutputStream(System.out, fileOut)
            val multiErr = MultiOutputStream(System.err, fileOut)
            
            // Set new streams
            System.setOut(PrintStream(multiOut))
            System.setErr(PrintStream(multiErr))
            
            println("=== Log started at ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)} ===")
            println("Logging to file: ${logFile.absolutePath}")
            
        } catch (e: Exception) {
            System.err.println("Failed to setup file logging: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Helper class to write to multiple OutputStreams simultaneously.
     */
    private class MultiOutputStream(vararg val streams: OutputStream) : OutputStream() {
        override fun write(b: Int) {
            for (stream in streams) {
                stream.write(b)
            }
        }
        
        override fun write(b: ByteArray) {
            for (stream in streams) {
                stream.write(b)
            }
        }
        
        override fun write(b: ByteArray, off: Int, len: Int) {
            for (stream in streams) {
                stream.write(b, off, len)
            }
        }
        
        override fun flush() {
            for (stream in streams) {
                stream.flush()
            }
        }
        
        override fun close() {
            for (stream in streams) {
                // Don't close System.out or System.err
                if (stream !== System.out && stream !== System.err) {
                    try {
                        stream.close()
                    } catch (e: Exception) {
                        // Ignore close errors
                    }
                }
            }
        }
    }
}
