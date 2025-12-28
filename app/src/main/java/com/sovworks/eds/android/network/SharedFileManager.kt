package com.sovworks.eds.android.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class SharedFileManager(private val context: Context) {
    private val sharedFiles = mutableSetOf<String>() // Liste von Pfaden oder IDs
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("shared_files", Context.MODE_PRIVATE)

    init {
        val saved = prefs.getString("files", null)
        if (saved != null) {
            val type = object : TypeToken<Set<String>>() {}.type
            sharedFiles.addAll(gson.fromJson(saved, type))
        }
    }

    fun shareFile(path: String) {
        sharedFiles.add(path)
        save()
    }

    fun unshareFile(path: String) {
        sharedFiles.remove(path)
        save()
    }

    private fun save() {
        prefs.edit().putString("files", gson.toJson(sharedFiles)).apply()
    }

    fun searchFiles(query: String): List<SharedFile> {
        // In einer echten Implementierung würden wir hier die tatsächlichen Metadaten der Dateien abrufen.
        // Für den Prototyp suchen wir in den Pfadnamen.
        return sharedFiles.filter { it.contains(query, ignoreCase = true) }
            .map { path ->
                val name = path.substringAfterLast('/')
                SharedFile(name = name, size = 0) // Größe müsste man aus dem FS holen
            }
    }

    companion object {
        @Volatile
        private var instance: SharedFileManager? = null

        fun getInstance(context: Context): SharedFileManager {
            return instance ?: synchronized(this) {
                instance ?: SharedFileManager(context).also { instance = it }
            }
        }
    }
}
