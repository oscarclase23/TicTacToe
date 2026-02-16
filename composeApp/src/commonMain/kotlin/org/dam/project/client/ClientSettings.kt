package org.dam.project.client

interface ClientSettings {
    fun saveString(key: String, value: String)
    fun getString(key: String): String?
}
