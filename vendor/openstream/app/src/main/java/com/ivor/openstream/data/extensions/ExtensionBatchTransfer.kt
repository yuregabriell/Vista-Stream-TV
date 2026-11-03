package com.ivor.openstream.data.extensions

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Portable *configuration* backup, not an executable plugin bundle. No tokens, credentials,
 * stream URLs, cached content or third-party code are included.
 */
@Serializable
data class ExtensionBackup(
    val format: String = "vista-extensions",
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val repositories: List<String> = emptyList(),
    val installed: List<ExtensionBackupEntry> = emptyList(),
    val sourceOrder: List<String> = emptyList()
)

@Serializable
data class ExtensionBackupEntry(
    val key: String,
    val enabled: Boolean = true
)

data class ExtensionImportSummary(
    val repositoriesAdded: Int,
    val extensionsInstalled: Int,
    val extensionsRestored: Int,
    val skipped: Int,
    val errors: List<String>
)

/** Pure codec with strict shape and size validation before touching local installation state. */
object ExtensionBatchTransfer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    const val MIME_TYPE = "application/json"
    const val FILE_NAME = "vistastream-extensoes.json"
    const val MAX_BYTES = 2_000_000

    fun encode(backup: ExtensionBackup): String = json.encodeToString(backup)

    fun decode(raw: String): ExtensionBackup {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "O ficheiro de extensões é demasiado grande."
        }
        val backup = json.decodeFromString<ExtensionBackup>(raw)
        require(backup.format == "vista-extensions" && backup.version == 1) {
            "Formato de backup de extensões incompatível."
        }
        require(backup.repositories.size <= 500 && backup.installed.size <= 5_000 &&
            backup.sourceOrder.size <= 5_000) {
            "O ficheiro contém entradas a mais."
        }
        require(backup.repositories.all { it.length in 1..2_048 } &&
            backup.installed.all { it.key.length in 1..512 } &&
            backup.sourceOrder.all { it.length in 1..512 }) {
            "O ficheiro contém endereços ou identificadores inválidos."
        }
        return backup.copy(
            repositories = backup.repositories.distinct(),
            installed = backup.installed.distinctBy { it.key },
            sourceOrder = backup.sourceOrder.distinct()
        )
    }
}
