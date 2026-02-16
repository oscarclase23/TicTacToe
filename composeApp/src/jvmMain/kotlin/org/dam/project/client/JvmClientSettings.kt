package org.dam.project.client

import java.io.File
import java.util.Properties

class JvmClientSettings : ClientSettings {
    private val file = File("client_settings.properties")
    private val props = Properties()
    
    init {
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        }
    }
    
    override fun saveString(key: String, value: String) {
        props.setProperty(key, value)
        file.outputStream().use { props.store(it, "Multiplayer Client Settings") }
    }
    
    override fun getString(key: String): String? {
        return props.getProperty(key)
    }
}
