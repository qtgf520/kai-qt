package com.qtkai.zhong.tools

import com.qtkai.zhong.network.tools.ParameterSchema
import com.qtkai.zhong.network.tools.Tool
import com.qtkai.zhong.network.tools.ToolSchema
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Tool that allows the AI to read and write files on the Android device's storage.
 * Requires MANAGE_EXTERNAL_STORAGE permission for full access.
 * Accessible paths: /storage/emulated/0/ and subdirectories.
 */
object AndroidDirectoryTool : Tool {
    @Serializable
    data class AndroidDirectoryArgs(
        val action: String = "read",
        val path: String = "/storage/emulated/0/",
        val content: String? = null,
        val recursive: Boolean = false
    )

    override val schema = ToolSchema(
        name = "android_directory",
        description = "Read, write, list, delete, or create files and directories in /storage/emulated/0/. Requires MANAGE_EXTERNAL_STORAGE permission. Paths: /storage/emulated/0/ and subdirectories. Actions: read, write, list, delete, mkdir",
        parameters = mapOf(
            "action" to ParameterSchema(
                type = "string",
                description = "Action to perform: 'read', 'write', 'list', 'delete', 'mkdir'",
                required = true
            ),
            "path" to ParameterSchema(
                type = "string",
                description = "File or directory path (e.g., /storage/emulated/0/Documents/)",
                required = true
            ),
            "content" to ParameterSchema(
                type = "string",
                description = "Content to write (required for 'write' action)",
                required = false
            ),
            "recursive" to ParameterSchema(
                type = "boolean",
                description = "List directories recursively (default: false)",
                required = false
            )
        )
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        return try {
            val action = args["action"]?.toString() ?: "read"
            val path = args["path"]?.toString() ?: "/storage/emulated/0/"
            val content = args["content"] as? String
            val recursive = args["recursive"] as? Boolean ?: false
            
            when (action) {
                "read" -> readFile(path)
                "write" -> writeFile(path, content ?: "")
                "list" -> listDirectory(path, recursive)
                "delete" -> deleteFile(path)
                "mkdir" -> createDir(path)
                else -> mapOf("error" to "Unknown action: $action")
            }
        } catch (e: Exception) {
            mapOf("error" to "Error: ${e.message}")
        }
    }

    private fun readFile(path: String): Any {
        val file = File(path)
        if (!file.exists()) return mapOf("error" to "File not found: $path")
        if (file.isDirectory) return mapOf("error" to "Is a directory, use 'list' action")
        if (!file.canRead()) return mapOf("error" to "Permission denied: $path")
        return mapOf("content" to file.readText(), "path" to path)
    }

    private fun writeFile(path: String, content: String): Any {
        val file = File(path)
        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        file.writeText(content)
        return mapOf("success" to true, "bytes" to content.length, "path" to path)
    }

    private fun listDirectory(path: String, recursive: Boolean): Any {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return mapOf("error" to "Not a directory: $path")
        if (!dir.canRead()) return mapOf("error" to "Permission denied: $path")
        val entries = if (recursive) {
            buildList {
                dir.walkTopDown().forEach { if (it.isFile) add(it.relativeTo(dir).toString()) }
            }
        } else {
            dir.listFiles()?.map { it.name }?.toList() ?: emptyList()
        }
        return mapOf("entries" to entries, "path" to path)
    }

    private fun deleteFile(path: String): Any {
        val file = File(path)
        if (!file.exists()) return mapOf("error" to "File not found: $path")
        if (!file.canWrite()) return mapOf("error" to "Permission denied: $path")
        if (file.isDirectory) {
            file.listFiles()?.forEach { it.delete() }
            file.delete()
        } else {
            file.delete()
        }
        return mapOf("success" to true, "path" to path)
    }

    private fun createDir(path: String): Any {
        val dir = File(path)
        if (dir.exists()) return mapOf("error" to "Directory already exists: $path")
        if (!dir.mkdirs()) return mapOf("error" to "Failed to create directory: $path")
        return mapOf("success" to true, "path" to path)
    }
}