package com.voxengine.engine.local

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.voxengine.audio.AudioUtils
import com.voxengine.engine.AudioCache
import com.voxengine.engine.AudioFormat
import com.voxengine.engine.SynthesisResult
import com.voxengine.engine.TTSEngine
import com.voxengine.engine.VoiceInfo
import com.voxengine.engine.VoiceType
import com.voxengine.util.SpeechTextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Truly free, on-device TTS engine built on sherpa-onnx. No network, no API key,
 * fully private. One or more models are downloaded on demand via [LocalModelManager]
 * (Kitten by default, Piper optional) and cached on disk; speech is synthesized
 * locally and returned as a WAV [SynthesisResult].
 *
 * The offline engine never needs a network connection once a model is installed,
 * so it also works as a zero-cost fallback alongside the online Edge engine.
 */
class LocalTTSEngine(private val context: Context) : TTSEngine {

    override val id = "local"
    override val name = "Local (Offline)"
    override val description = "Free on-device TTS via sherpa-onnx. No network or API key."
    override val supportsVoiceClone = false
    override val supportsVoiceDesign = false

    private val initMutex = Mutex()

    // Cache of initialized sherpa-onnx runtimes, keyed by model family id.
    private val runtimes = HashMap<String, OfflineTts>()

    /** Number of inference threads; balanced for typical phone CPUs. */
    private val numThreads = 2

    override suspend fun synthesize(
        text: String,
        voice: String,
        style: String?,
        optimizeTextPreview: Boolean
    ): SynthesisResult = withContext(Dispatchers.IO) {
        val speechText = SpeechTextNormalizer.normalize(text)
        if (!SpeechTextNormalizer.hasSpeakableContent(speechText)) {
            val silence = AudioUtils.silentWav()
            return@withContext SynthesisResult(
                audioData = silence,
                format = AudioFormat.WAV,
                sampleRate = AudioUtils.getWavSampleRate(silence),
                elapsedMs = 0
            )
        }

        val (modelId, sid) = parseVoice(voice)
        val spec = LocalModelManager.allModels.firstOrNull { it.id == modelId }
            ?: return@withContext SynthesisResult(
                audioData = AudioUtils.silentWav(), format = AudioFormat.WAV, sampleRate = 24000, elapsedMs = 0
            )
        if (!LocalModelManager.isUsable(context, spec)) {
            throw IllegalStateException("Local model '${spec.name}' is not downloaded. Install it in Settings → Local voices.")
        }

        val cacheKey = AudioCache.generateKey(
            text = speechText, voice = voice, style = style, engineId = id,
            voiceFingerprint = "$modelId:$sid"
        )
        AudioCache.get(cacheKey)?.let { cached ->
            return@withContext SynthesisResult(
                audioData = cached, format = AudioFormat.WAV,
                sampleRate = AudioUtils.getWavSampleRate(cached), elapsedMs = 0
            )
        }

        val start = System.currentTimeMillis()
        val tts = getRuntime(spec, sid)
        val audio = tts.generate(speechText, sid, 1.0f)
            ?: throw IllegalStateException("sherpa-onnx returned no audio")

        val pcm = floatToPcm16(audio.samples)
        val wav = AudioUtils.pcmToWav(pcm, audio.sampleRate, 1, 16)
        AudioCache.put(cacheKey, wav)
        SynthesisResult(
            audioData = wav,
            format = AudioFormat.WAV,
            sampleRate = audio.sampleRate,
            elapsedMs = System.currentTimeMillis() - start
        )
    }

    /** Build (and cache) the sherpa-onnx [OfflineTts] for the given model. */
    private suspend fun getRuntime(spec: LocalModelSpec, sid: Int): OfflineTts {
        initMutex.withLock {
            runtimes[spec.family.name]?.let { return it }
            val tts = when (spec.family) {
                LocalEngineFamily.KITTEN -> createKittenTts(spec)
                LocalEngineFamily.PIPER -> createPiperTts(spec)
            }
            runtimes[spec.family.name] = tts
            return tts
        }
    }

    private fun createKittenTts(spec: LocalModelSpec): OfflineTts {
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kitten = OfflineTtsKittenModelConfig(
                    model = LocalModelManager.file(context, spec, "model.fp16.onnx"),
                    voices = LocalModelManager.file(context, spec, "voices.bin"),
                    tokens = LocalModelManager.file(context, spec, "tokens.txt"),
                    dataDir = LocalModelManager.file(context, spec, "espeak-ng-data")
                ),
                numThreads = numThreads,
                debug = false
            )
        )
        return OfflineTts(config = config)
    }

    private fun createPiperTts(spec: LocalModelSpec): OfflineTts {
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = LocalModelManager.file(context, spec, spec.onnxFileName),
                    tokens = LocalModelManager.file(context, spec, "tokens.txt"),
                    dataDir = LocalModelManager.file(context, spec, "espeak-ng-data"),
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                ),
                numThreads = numThreads,
                debug = false
            )
        )
        return OfflineTts(config = config)
    }

    override suspend fun getVoices(): List<VoiceInfo> {
        val result = mutableListOf<VoiceInfo>()
        val usable = LocalModelManager.allModels.filter { LocalModelManager.isUsable(context, it) }
        for (spec in usable) {
            when (spec.family) {
                LocalEngineFamily.KITTEN -> {
                    LocalModelManager.kittenVoices().forEach { v ->
                        result += VoiceInfo(
                            id = "${spec.id}:${v.sid}",
                            name = v.name,
                            description = "Kitten • ${v.gender} English",
                            type = VoiceType.PRESET,
                            engineId = id,
                            gender = v.gender,
                            tags = listOf("Offline", "English")
                        )
                    }
                }
                LocalEngineFamily.PIPER -> {
                    result += VoiceInfo(
                        id = "${spec.id}:0",
                        name = spec.voiceName,
                        description = "Piper • ${spec.gender} English",
                        type = VoiceType.PRESET,
                        engineId = id,
                        gender = spec.gender,
                        tags = listOf("Offline", "English")
                    )
                }
            }
        }
        return result
    }

    override suspend fun getStyles(): List<String> = emptyList()

    override fun isConfigured(): Boolean =
        LocalModelManager.allModels.any { LocalModelManager.isUsable(context, it) }

    override suspend fun cloneVoice(name: String, referenceAudio: ByteArray): VoiceInfo {
        throw NotImplementedError("Local (offline) TTS does not support voice cloning")
    }

    override suspend fun designVoice(description: String): VoiceInfo {
        throw NotImplementedError("Local (offline) TTS does not support voice design")
    }

    /** Voice ids are "<modelId>:<sid>"; voice names (Settings-default path) resolve by name. */
    private fun parseVoice(voice: String): Pair<String, Int> {
        if (voice.contains(':')) {
            val parts = voice.split(':')
            val modelId = parts[0]
            val sid = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return modelId to sid
        }
        // Settings saves voice.name (e.g. "Bella", "Alan"); map back to a model + speaker id.
        for (spec in LocalModelManager.allModels) {
            when (spec.family) {
                LocalEngineFamily.KITTEN -> {
                    LocalModelManager.kittenVoices().firstOrNull { it.name == voice }?.let {
                        return spec.id to it.sid
                    }
                }
                LocalEngineFamily.PIPER -> {
                    if (spec.voiceName == voice) return spec.id to 0
                }
            }
        }
        return LocalModelManager.KITTEN_DEFAULT to 0
    }

    private fun floatToPcm16(samples: FloatArray): ByteArray {
        val pcm = ByteArray(samples.size * 2)
        var i = 0
        for (s in samples) {
            val v = (s.coerceIn(-1f, 1f) * 32767).toInt()
            val lsb = (v and 0xFF)
            val msb = ((v shr 8) and 0xFF)
            pcm[i++] = lsb.toByte()
            pcm[i++] = msb.toByte()
        }
        return pcm
    }
}
