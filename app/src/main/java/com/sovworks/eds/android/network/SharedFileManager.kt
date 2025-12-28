package com.sovworks.eds.android.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class SharedFileManager(private val context: Context) {
    private val sharedFiles = mutableSetOf<String>()
    private val fileHashes = mutableMapOf<String, String>() // Pfad -> Hash
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("shared_files", Context.MODE_PRIVATE)

    init {
        val saved = prefs.getString("files", null)
        if (saved != null) {
            val type = object : TypeToken<Set<String>>() {}.type
            sharedFiles.addAll(gson.fromJson(saved, type))
        }
        val savedHashes = prefs.getString("hashes", null)
        if (savedHashes != null) {
            val type = object : TypeToken<Map<String, String>>() {}.type
            fileHashes.putAll(gson.fromJson(savedHashes, type))
        }
    }

    fun updateHash(path: String, hash: String) {
        fileHashes[path] = hash
        save()
    }

    fun getHash(path: String): String? = fileHashes[path]

    fun getAllSharedFiles(): List<String> = sharedFiles.toList()

    fun shareFile(path: String) {
        sharedFiles.add(path)
        save()
    }

    fun unshareFile(path: String) {
        sharedFiles.remove(path)
        fileHashes.remove(path)
        save()
    }

    private fun save() {
        prefs.edit()
            .putString("files", gson.toJson(sharedFiles))
            .putString("hashes", gson.toJson(fileHashes))
            .apply()
    }

    fun searchFiles(
        query: String,
        fileTypes: List<String> = emptyList(),
        minSizeBytes: Long? = null,
        maxSizeBytes: Long? = null
    ): List<SharedFile> {
        val normalizedTypes = normalizeFileTypes(fileTypes)
        return sharedFiles.filter { path ->
            path.contains(query, ignoreCase = true)
        }.mapNotNull { path ->
            val name = path.substringAfterLast('/')
            val size = File(path).length()
            if (!matchesFileType(name, normalizedTypes)) return@mapNotNull null
            if (minSizeBytes != null && size < minSizeBytes) return@mapNotNull null
            if (maxSizeBytes != null && size > maxSizeBytes) return@mapNotNull null
            SharedFile(name = name, size = size, hash = fileHashes[path])
        }
    }

    private fun normalizeFileTypes(fileTypes: List<String>): Set<String> {
        return fileTypes.mapNotNull { entry ->
            val trimmed = entry.trim().lowercase().removePrefix(".")
            trimmed.takeIf { it.isNotEmpty() }
        }.toSet()
    }

    private fun matchesFileType(fileName: String, fileTypes: Set<String>): Boolean {
        if (fileTypes.isEmpty()) return true
        val extension = fileName.substringAfterLast('.', "")
        if (extension.isEmpty()) return false
        return fileTypes.contains(extension.lowercase())
    }

    fun getFilePath(fileName: String): String? {
        return sharedFiles.find { it.endsWith("/$fileName") || it == fileName }
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
