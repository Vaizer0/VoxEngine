package com.voxengine.engine.edge

import com.voxengine.audio.AudioUtils
import com.voxengine.data.SettingsRepository
import com.voxengine.engine.AudioCache
import com.voxengine.engine.AudioFormat
import com.voxengine.engine.TTSEngine
import com.voxengine.engine.VoiceInfo
import com.voxengine.engine.SynthesisResult
import com.voxengine.engine.VoiceType
import com.voxengine.util.SpeechTextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EdgeTTSEngine(
    private val settingsRepository: SettingsRepository
) : TTSEngine {

    override val id = "edge"
    override val name = "Edge TTS"
    override val description = "Free Microsoft Edge TTS engine"
    override val supportsVoiceClone = false
    override val supportsVoiceDesign = false

    private val client by lazy { EdgeTTSClient() }

    override suspend fun synthesize(
        text: String,
        voice: String,
        style: String?,
        optimizeTextPreview: Boolean
    ): SynthesisResult = withContext(Dispatchers.IO) {
        val speechText = SpeechTextNormalizer.normalize(text)
        val resolvedVoice = resolveVoiceId(voice)
        // 纯标点/省略号等无可朗读内容的段落（如小说里单独成段的“……”）：直接跳过网络请求给一小段静音，
        // 既保留自然停顿，又省去一次必然空音频的 WebSocket 往返。Edge 端真返回空音频时由 EdgeTTSClient 兜底。
        if (!SpeechTextNormalizer.hasSpeakableContent(speechText)) {
            val silence = AudioUtils.silentWav()
            return@withContext SynthesisResult(
                audioData = silence,
                format = AudioFormat.WAV,
                sampleRate = AudioUtils.getWavSampleRate(silence),
                elapsedMs = 0
            )
        }
        val cacheKey = AudioCache.generateKey(
            text = speechText,
            voice = voice,
            style = style,
            engineId = id,
            voiceFingerprint = resolvedVoice
        )
        AudioCache.get(cacheKey)?.let { cached ->
            return@withContext SynthesisResult(
                audioData = cached,
                format = AudioFormat.WAV,
                sampleRate = AudioUtils.getWavSampleRate(cached),
                elapsedMs = 0
            )
        }

        val startTime = System.currentTimeMillis()
        val wav = client.synthesize(speechText, resolvedVoice)
        AudioCache.put(cacheKey, wav)
        SynthesisResult(
            audioData = wav,
            format = AudioFormat.WAV,
            sampleRate = AudioUtils.getWavSampleRate(wav),
            elapsedMs = System.currentTimeMillis() - startTime
        )
    }

    override suspend fun getVoices(): List<VoiceInfo> {
        return listOf(
            VoiceInfo("zh-CN-XiaoxiaoNeural", "晓晓", "Chinese female", VoiceType.PRESET, id, displayName = "Xiaoxiao"),
            VoiceInfo("zh-CN-YunxiNeural", "云希", "Chinese male", VoiceType.PRESET, id, displayName = "Yunxi"),
            VoiceInfo("zh-CN-YunjianNeural", "云健", "Chinese male", VoiceType.PRESET, id, displayName = "Yunjian"),
            VoiceInfo("zh-CN-XiaoyiNeural", "晓伊", "Chinese female", VoiceType.PRESET, id, displayName = "Xiaoyi"),
            VoiceInfo("zh-CN-YunyangNeural", "云扬", "Chinese male (news)", VoiceType.PRESET, id, displayName = "Yunyang"),
            VoiceInfo("zh-CN-liaoning-XiaobeiNeural", "晓北", "Chinese female (Northeast dialect)", VoiceType.PRESET, id, displayName = "Xiaobei"),
            VoiceInfo("zh-CN-shaanxi-XiaoniNeural", "晓妮", "Chinese female (Shaanxi dialect)", VoiceType.PRESET, id, displayName = "Xiaoni"),
            VoiceInfo("zh-HK-HiuMaanNeural", "曉曼", "Cantonese female", VoiceType.PRESET, id, displayName = "HiuMaan"),
            VoiceInfo("zh-TW-HsiaoChenNeural", "曉臻", "Taiwanese female", VoiceType.PRESET, id, displayName = "HsiaoChen"),
            VoiceInfo("en-US-JennyNeural", "Jenny", "English female", VoiceType.PRESET, id),
            VoiceInfo("en-US-GuyNeural", "Guy", "English male", VoiceType.PRESET, id),
            VoiceInfo("en-US-AriaNeural", "Aria", "English female", VoiceType.PRESET, id),
            VoiceInfo("en-US-AndrewNeural", "Andrew", "English male", VoiceType.PRESET, id),
            VoiceInfo("en-US-AndrewMultilingualNeural", "Andrew (multilingual)", "English male (multilingual)", VoiceType.PRESET, id),
            // Japanese voices (MiMo doesn't support Japanese, it reads Kanji as Chinese; use these Edge voices for Japanese)
            VoiceInfo("ja-JP-NanamiNeural", "七海", "Japanese female", VoiceType.PRESET, id, displayName = "Nanami"),
            VoiceInfo("ja-JP-KeitaNeural", "圭太", "Japanese male", VoiceType.PRESET, id, displayName = "Keita"),
            VoiceInfo("ja-JP-AoiNeural", "葵", "Japanese female", VoiceType.PRESET, id, displayName = "Aoi"),
            VoiceInfo("ja-JP-DaichiNeural", "大智", "Japanese male", VoiceType.PRESET, id, displayName = "Daichi"),
            VoiceInfo("ja-JP-ShioriNeural", "诗织", "Japanese female", VoiceType.PRESET, id, displayName = "Shiori"),
            VoiceInfo("ja-JP-NaokiNeural", "直树", "Japanese male", VoiceType.PRESET, id, displayName = "Naoki"),
            VoiceInfo("ja-JP-MayuNeural", "真由", "Japanese female", VoiceType.PRESET, id, displayName = "Mayu")
        )
    }

    override suspend fun getStyles(): List<String> = emptyList()

    /** Settings page saves voice.name, Reader saves voice.id; resolve both back to the full Edge voice id. */
    private suspend fun resolveVoiceId(voice: String): String {
        if (voice.contains("Neural")) return voice
        val match = getVoices().firstOrNull { it.name == voice || it.id == voice }
        return match?.id ?: "zh-CN-XiaoxiaoNeural"
    }

    override fun isConfigured(): Boolean = true

    override suspend fun cloneVoice(name: String, referenceAudio: ByteArray): VoiceInfo {
        throw NotImplementedError("Edge TTS does not support voice cloning")
    }

    override suspend fun designVoice(description: String): VoiceInfo {
        throw NotImplementedError("Edge TTS does not support voice design")
    }
}
