package com.voxengine.util

/** 合成错误的用户可读文案，供 Reader / 系统 TTS / Test 共用。
 *  已知模式（限流 / API Key）给出解释性文案；其余仅回原始 message，由调用方加自己的上下文前缀。 */
object TtsErrors {
    fun friendly(error: Throwable?): String {
        val message = error?.message.orEmpty()
        return when {
            message.contains("429") -> "Synthesis is being rate-limited. Increase the paragraph gap or lower the concurrency and try again."
            message.contains("API Key", ignoreCase = true) || message.contains("未配置") -> "API Key is not configured or invalid. Please check it in Settings."
            else -> message.ifBlank { "Unknown error" }
        }
    }
}
