package com.senecapp.data

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class SavedOrganizationListStore(private val directory: File) {
    suspend fun save(uid: String, items: List<Organization>) = withContext(Dispatchers.IO) {
        lock.withLock {
            val file = file(uid)
            val data = JSONObject().put("version", 1).put("items", JSONArray(items.map(OrganizationJson::encode)))
            val output = file.startWrite()
            try {
                output.write(data.toString().toByteArray(Charsets.UTF_8))
                file.finishWrite(output)
            } catch (failure: Exception) {
                file.failWrite(output)
                throw failure
            }
        }
    }

    suspend fun read(uid: String): List<Organization>? = withContext(Dispatchers.IO) {
        lock.withLock {
            val data = try {
                file(uid).readFully()
            } catch (_: FileNotFoundException) {
                return@withLock null
            }
            val root = JSONObject(data.toString(Charsets.UTF_8))
            require(root.getInt("version") == 1)
            root.getJSONArray("items").readItems(OrganizationJson::decode)
        }
    }

    private fun file(uid: String): AtomicFile {
        require(uid.isNotBlank())
        val digest = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return AtomicFile(File(directory, "organizations-$name.json"))
    }

    private companion object { val lock = Mutex() }
}
