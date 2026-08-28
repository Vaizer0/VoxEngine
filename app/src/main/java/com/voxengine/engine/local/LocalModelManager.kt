package com.voxengine.engine.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.utils.IOUtils
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

enum class LocalEngineFamily { KITTEN, PIPER }

/** One selectable on-device voice exposed by a downloaded model. */
data class LocalVoiceDef(
    val sid: Int,
    val name: String,
    val gender: String
)

/**
 * A downloadable on-device TTS model backed by sherpa-onnx.
 *
 * Each spec points at the official `.tar.bz2` model archive published under the
 * `tts-models` tag of k2-fsa/sherpa-onnx. After download + extraction the model
 * files live under `filesDir/models/<id>/` with a `.ready` marker to record
 * completion.
 */
data class LocalModelSpec(
    val id: String,
    val name: String,
    val description: String,
    val family: LocalEngineFamily,
    val archiveUrl: String,
    val rootDirName: String,
    val approxSizeMb: Int,
    val requiredFiles: List<String>
)

sealed interface LocalModelState {
    data object NotDownloaded : LocalModelState
    data class Downloading(val progress: Float) : LocalModelState
    data object Ready : LocalModelState
    data class Failed(val message: String) : LocalModelState
}

object LocalModelManager {

    const val KITTEN_DEFAULT = "kitten-nano-en-v0_1-fp16"

    private const val BASE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
    private const val READY_MARKER = ".ready"

    /** Default fast English model (Kitten, 8 voices). */
    val kitten = LocalModelSpec(
        id = "kitten-nano-en-v0_1-fp16",
        name = "Kitten (English)",
        description = "Fast on-device English model, 8 voices (4 male, 4 female). Recommended default.",
        family = LocalEngineFamily.KITTEN,
        archiveUrl = "$BASE_URL/kitten-nano-en-v0_1-fp16.tar.bz2",
        rootDirName = "kitten-nano-en-v0_1-fp16",
        approxSizeMb = 27,
        requiredFiles = listOf("model.fp16.onnx", "voices.bin", "tokens.txt", "espeak-ng-data")
    )

    /** Optional higher-quality English Piper (VITS) model. */
    val piper = LocalModelSpec(
        id = "vits-piper-en_US-lessac-medium",
        name = "Piper: Lessac (English)",
        description = "Higher-quality English Piper/VITS voice. Larger download.",
        family = LocalEngineFamily.PIPER,
        archiveUrl = "$BASE_URL/vits-piper-en_US-lessac-medium.tar.bz2",
        rootDirName = "vits-piper-en_US-lessac-medium",
        approxSizeMb = 63,
        requiredFiles = listOf("en_US-lessac-medium.onnx", "tokens.txt", "espeak-ng-data")
    )

    val allModels: List<LocalModelSpec> = listOf(kitten, piper)

    /** Kitten's 8 built-in voices mapped from sid -> friendly name/gender (docs: sid->name). */
    fun kittenVoices(): List<LocalVoiceDef> = listOf(
        LocalVoiceDef(0, "Jasper", "Male"),
        LocalVoiceDef(1, "Bella", "Female"),
        LocalVoiceDef(2, "Bruno", "Male"),
        LocalVoiceDef(3, "Luna", "Female"),
        LocalVoiceDef(4, "Hugo", "Male"),
        LocalVoiceDef(5, "Rosie", "Female"),
        LocalVoiceDef(6, "Leo", "Male"),
        LocalVoiceDef(7, "Kiki", "Female")
    )

    private val _state = MutableStateFlow<Map<String, LocalModelState>>(emptyMap())
    val state: StateFlow<Map<String, LocalModelState>> = _state.asStateFlow()

    @Volatile
    private var modelsDir: File? = null

    private val okHttp by lazy {
        OkHttpClient.Builder().build()
    }

    // ---- Paths -------------------------------------------------------------

    private fun modelsDir(context: Context): File {
        modelsDir?.let { return it }
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        modelsDir = dir
        return dir
    }

    fun modelDir(context: Context, spec: LocalModelSpec): File =
        File(modelsDir(context), spec.id)

    fun file(context: Context, spec: LocalModelSpec, relative: String): String =
        File(modelDir(context, spec), relative).absolutePath

    fun isReady(context: Context, spec: LocalModelSpec): Boolean =
        isReadyFile(modelDir(context, spec)).exists()

    // ---- State -------------------------------------------------------------

    private fun currentState(spec: LocalModelSpec): LocalModelState =
        _state.value[spec.id] ?: LocalModelState.NotDownloaded

    private fun setState(spec: LocalModelSpec, s: LocalModelState) {
        val snapshot = _state.value.toMutableMap()
        snapshot[spec.id] = s
        _state.value = snapshot
    }

    /** Returns true if the model is usable for synthesis. */
    fun isUsable(context: Context, spec: LocalModelSpec): Boolean {
        val ready = isReady(context, spec)
        if (ready) return true
        // Already known-good in-memory state
        return currentState(spec) == LocalModelState.Ready && isReadyFile(modelDir(context, spec)).exists()
    }

    fun stateFor(spec: LocalModelSpec): LocalModelState = currentState(spec)

    // ---- Download + extract -------------------------------------------------

    suspend fun download(context: Context, spec: LocalModelSpec): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (isUsable(context, spec)) {
                setState(spec, LocalModelState.Ready)
                return@withContext Result.success(Unit)
            }
            setState(spec, LocalModelState.Downloading(0f))
            try {
                val dir = modelDir(context, spec)
                dir.mkdirs()
                val archive = File(dir, "__model.tar.bz2")

                // Stream download with progress
                val request = Request.Builder().url(spec.archiveUrl).build()
                okHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code} downloading model")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty response body")
                    val total = body.contentLength()
                    val sink = archive.sink().buffer()
                    body.source().use { source ->
                        val buffer = okio.Buffer()
                        var written = 0L
                        while (!source.exhausted()) {
                            val read = source.read(buffer, 8192)
                            if (read == -1L) break
                            written += read
                            sink.write(buffer, read)
                            if (total > 0) {
                                setState(spec, LocalModelState.Downloading((written.toFloat() / total).coerceIn(0f, 1f)))
                            }
                        }
                    }
                    sink.close()
                }

                setState(spec, LocalModelState.Downloading(0.99f))

                // Extract .tar.bz2, stripping the single top-level folder
                dir.listFiles()?.filter { it.name != "__model.tar.bz2" && it.name != READY_MARKER }
                    ?.forEach { it.deleteRecursively() }
                extractTarBz2(archive, dir, spec.rootDirName)
                archive.delete()

                verifyRequiredFiles(dir, spec.requiredFiles, spec)

                isReadyFile(dir).writeText(System.currentTimeMillis().toString())
                setState(spec, LocalModelState.Ready)
                Result.success(Unit)
            } catch (t: Throwable) {
                val message = t.message ?: t.javaClass.simpleName
                setState(spec, LocalModelState.Failed(message))
                Result.failure(t)
            }
        }

    fun delete(context: Context, spec: LocalModelSpec) {
        val dir = modelDir(context, spec)
        dir.deleteRecursively()
        setState(spec, LocalModelState.NotDownloaded)
    }

    private fun extractTarBz2(archive: File, dest: File, rootDirName: String) {
        TarArchiveInputStream(
            BZip2CompressorInputStream(BufferedInputStream(archive.inputStream()))
        ).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                val rawName = entry.name ?: ""
                // Strip the common single top-level folder (e.g. "kitten-nano-en-v0_1-fp16/...")
                val rel = stripTopLevel(rawName, rootDirName)
                if (rel.isBlank()) {
                    entry = tar.nextEntry
                    continue
                }
                val outFile = File(dest, rel)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(outFile)).use { os ->
                        IOUtils.copy(tar, os)
                    }
                }
                entry = tar.nextEntry
            }
        }
    }

    private fun stripTopLevel(rawName: String, rootDirName: String): String {
        val normalized = rawName.removePrefix("./")
        val parts = normalized.split('/')
        if (parts.size <= 1) return normalized
        val first = parts.first()
        val useFirst = first == rootDirName || first.startsWith(rootDirName)
        return if (useFirst) parts.drop(1).joinToString("/") else normalized
    }

    private fun verifyRequiredFiles(dir: File, required: List<String>, spec: LocalModelSpec) {
        val missing = required.filter { rel ->
            if (rel == "espeak-ng-data") !File(dir, rel).isDirectory
            else !File(dir, rel).isFile
        }
        if (missing.isNotEmpty()) {
            throw IllegalStateException("Model extraction incomplete; missing: ${missing.joinToString(", ")}")
        }
    }

    private fun isReadyFile(dir: File): File = File(dir, READY_MARKER)
}
