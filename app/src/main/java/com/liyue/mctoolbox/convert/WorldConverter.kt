package com.liyue.mctoolbox.convert

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.frenetic.litechunk.api.ChunkerEngine
import com.frenetic.litechunk.api.ConversionOptions
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Thin façade over the Chunker engine that lives in `:engine`. The engine wants plain
 * directories, while Android hands us a SAF tree, so every operation starts by
 * materialising the picked folder into the app cache.
 */
object WorldConverter {
    private const val ENGINE_CLASS = "com.frenetic.litechunk.engine.EngineImpl"

    @Volatile
    private var engine: ChunkerEngine? = null

    fun engine(context: Context): ChunkerEngine {
        engine?.let { return it }
        synchronized(this) {
            engine?.let { return it }
            val impl = Class.forName(ENGINE_CLASS).getDeclaredConstructor().newInstance()
            val instance = impl as ChunkerEngine
            instance.setResourceDirectory(syncResources(context))
            val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
            instance.setConcurrency(threads)
            // Lean mode trades a little speed for a much smaller working set, which
            // matters because these worlds are measured in gigabytes.
            val bigHeap = Runtime.getRuntime().maxMemory() > 700L * 1024 * 1024
            runCatching { instance.setLeanMode(!bigHeap) }
            engine = instance
            return instance
        }
    }

    private fun syncResources(context: Context): File {
        val target = File(context.filesDir, "engine-res")
        val assets = context.assets
        fun copy(path: String) {
            val out = File(target, path)
            if (out.isFile && out.length() > 0) return
            out.parentFile?.mkdirs()
            assets.open("engine-res/$path").use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
        }
        runCatching {
            copy("java/default_font_width_codepoints.json")
            copy("java/CavesAndCliffsHeightPack.zip")
        }
        return target
    }

    /**
     * Copies a SAF tree into [destination]. Returns the directory that should be
     * handed to the engine, or null when the tree holds no world at all.
     */
    fun materialize(context: Context, tree: Uri, destination: File): File? {
        destination.deleteRecursively()
        destination.mkdirs()
        val resolver = context.contentResolver
        val rootDocId = DocumentsContract.getTreeDocumentId(tree)
        val label = rootDocId.substringAfterLast(':').substringAfterLast('/')
        val root = File(destination, label.ifBlank { "world" })
        root.mkdirs()
        var copied = 0
        fun walk(documentId: String, out: File) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
            resolver.query(children, null, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIndex) ?: continue
                    val childName = cursor.getString(nameIndex) ?: continue
                    val mime = cursor.getString(mimeIndex) ?: ""
                    val child = File(out, childName)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        child.mkdirs()
                        walk(id, child)
                    } else {
                        runCatching {
                            val childUri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                            resolver.openInputStream(childUri)?.use { input ->
                                FileOutputStream(child).use { output -> input.copyTo(output) }
                            }
                            copied++
                        }
                    }
                }
            }
        }
        walk(rootDocId, root)
        if (copied == 0) return null
        if (File(root, "level.dat").isFile || File(root, "db").isDirectory) return root
        root.listFiles()?.firstOrNull { dir ->
            dir.isDirectory && (File(dir, "level.dat").isFile || File(dir, "db").isDirectory)
        }?.let { return it }
        return root
    }


    /** Expands a picked .zip / .mcworld into [destination] and returns the world folder. */
    fun unpack(context: Context, uri: Uri, destination: File, displayName: String): File? {
        destination.deleteRecursively()
        destination.mkdirs()
        val zipFile = File(destination, "source.zip")
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(zipFile).use { output -> input.copyTo(output) }
            } ?: return null
        }.onFailure { return null }
        if (zipFile.length() == 0L) return null
        val extract = File(destination, "content")
        extract.mkdirs()
        runCatching {
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    if (name.startsWith("__MACOSX/") || name.contains("../")) {
                        entry = zip.nextEntry
                        continue
                    }
                    val target = File(extract, name)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { output -> zip.copyTo(output) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }.onFailure { return null }
        zipFile.delete()
        if (File(extract, "level.dat").isFile || File(extract, "db").isDirectory) return extract
        extract.listFiles()?.firstOrNull { dir ->
            dir.isDirectory && (File(dir, "level.dat").isFile || File(dir, "db").isDirectory)
        }?.let { return it }
        return if (extract.listFiles()?.isNotEmpty() == true) extract else null
    }

    fun detect(context: Context, worldDir: File) = runCatching {
        engine(context).detect(worldDir)
    }.getOrNull()

    fun convert(
        context: Context,
        input: File,
        output: File,
        formatId: String,
        onProgress: (Float, String) -> Unit,
    ) {
        output.deleteRecursively()
        output.mkdirs()
        engine(context).convert(
            input,
            output,
            formatId,
            ConversionOptions.DEFAULT,
        ) { fraction, stage -> onProgress(fraction.toFloat(), stage ?: "") }
    }

    fun zip(source: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile).buffered()).use { zip ->
            val base = source.toPath()
            source.walkTopDown().forEach { file ->
                if (file == source) return@forEach
                val relative = base.relativize(file.toPath()).toString().replace('\\', '/')
                if (file.isDirectory) {
                    zip.putNextEntry(ZipEntry("$relative/"))
                    zip.closeEntry()
                } else {
                    zip.putNextEntry(ZipEntry(relative))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }
}
