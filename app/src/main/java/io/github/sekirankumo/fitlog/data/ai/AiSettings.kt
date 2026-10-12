package io.github.sekirankumo.fitlog.data.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 一个 OpenAI 兼容的 AI 服务（Gemini、智谱、通义等都提供兼容接口） */
@Serializable
data class AiProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
)

/** 某个用途使用哪个服务的哪个模型 */
@Serializable
data class ModelRef(
    val providerId: String,
    val model: String,
)

@Serializable
data class AiSettings(
    val providers: List<AiProvider> = emptyList(),
    /** AI 助手：对话 + 工具调用 + 文本解析 */
    val assistantModel: ModelRef? = null,
    /** 图片识别：需要支持看图 */
    val visionModel: ModelRef? = null,
    /** 为 true 时 AI 的修改操作不再弹确认 */
    val autoExecuteWrites: Boolean = false,
) {
    fun provider(id: String?): AiProvider? = providers.firstOrNull { it.id == id }
}

private val Context.aiDataStore by preferencesDataStore(name = "ai_settings")

/** AI 设置（含 API Key）单独存放，并在 backup 规则里排除，不会进入系统云备份 */
class AiSettingsRepository(private val context: Context) {
    private val key = stringPreferencesKey("settings_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settings: Flow<AiSettings> = context.aiDataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString<AiSettings>(it) }.getOrNull() } ?: AiSettings()
    }

    suspend fun current(): AiSettings = settings.first()

    suspend fun update(transform: (AiSettings) -> AiSettings) {
        context.aiDataStore.edit { prefs ->
            val old = prefs[key]?.let { runCatching { json.decodeFromString<AiSettings>(it) }.getOrNull() } ?: AiSettings()
            prefs[key] = json.encodeToString(AiSettings.serializer(), transform(old))
        }
    }
}

/** 常用服务商的接口地址模板 */
data class ProviderPreset(val name: String, val baseUrl: String, val hint: String)

val PROVIDER_PRESETS = listOf(
    ProviderPreset("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4/", "glm-4-flash、glm-4v-flash 免费"),
    ProviderPreset("硅基流动", "https://api.siliconflow.cn/v1/", "有多款免费模型"),
    ProviderPreset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1/", "qwen 系列"),
    ProviderPreset("DeepSeek", "https://api.deepseek.com/v1/", "不支持看图"),
    ProviderPreset("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-2.5-flash 有免费额度"),
    ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1/", "模型名带 :free 的免费"),
    ProviderPreset("Groq", "https://api.groq.com/openai/v1/", "速度快，有免费额度"),
    ProviderPreset("自定义", "", "任意 OpenAI 兼容接口"),
)
