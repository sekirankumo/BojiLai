package io.github.sekirankumo.fitlog.data.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

/** AI 设置（含 API Key）单独存放，并在 backup 规则里排除，不会进入系统云备份 */
class AiSettingsRepository(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("settings_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settings: Flow<AiSettings> = store.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString<AiSettings>(it) }.getOrNull() } ?: AiSettings()
    }

    suspend fun current(): AiSettings = settings.first()

    suspend fun update(transform: (AiSettings) -> AiSettings) {
        store.edit { prefs ->
            val old = prefs[key]?.let { runCatching { json.decodeFromString<AiSettings>(it) }.getOrNull() } ?: AiSettings()
            prefs[key] = json.encodeToString(AiSettings.serializer(), transform(old))
        }
    }
}

/**
 * 常用的 AI 服务：接口地址、创建 Key 的页面、推荐模型和几个标签，「添加 AI 服务」的引导用。
 * 链接和模型名 2026-10 按各家官网核对过；厂商改名时，推荐模型会和服务实际提供的模型列表对一下再用，
 * 对不上就不自动填，让用户自己选。
 * @param chatModels 推荐给 AI 助手的模型（要能调用工具），按优先级排
 * @param visionModels 推荐给图片识别的模型；空表示这家不能看图
 * @param cost 费用标签；free 为 true 时标成绿色
 * @param china 国内网络能否直接访问
 * @param note 注册时要注意的事
 */
data class ProviderPreset(
    val name: String,
    val baseUrl: String,
    val intro: String,
    val keyUrl: String? = null,
    val chatModels: List<String> = emptyList(),
    val visionModels: List<String> = emptyList(),
    val cost: String = "",
    val free: Boolean = false,
    val china: Boolean = true,
    val note: String? = null,
)

val PROVIDER_PRESETS = listOf(
    ProviderPreset(
        name = "智谱 GLM",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4/",
        intro = "推荐。自动配上的 glm-4.7-flash（对话）和 glm-4.6v-flash（看图）都免费，注册完就能用。",
        keyUrl = "https://bigmodel.cn/usercenter/proj-mgmt/apikeys",
        chatModels = listOf("glm-4.7-flash", "glm-4.5-flash", "glm-4-flash-250414"),
        visionModels = listOf("glm-4.6v-flash", "glm-4v-flash"),
        cost = "有免费模型",
        free = true,
        note = "用手机号注册；如果提示实名认证，按页面完成就行。",
    ),
    ProviderPreset(
        name = "硅基流动",
        baseUrl = "https://api.siliconflow.cn/v1/",
        intro = "模型很多。自动配的是常用的小模型；哪些免费以平台「模型广场」的标注为准。",
        keyUrl = "https://cloud.siliconflow.cn/account/ak",
        chatModels = listOf("Qwen/Qwen3-8B", "THUDM/GLM-4-9B-0414", "Qwen/Qwen2.5-7B-Instruct"),
        visionModels = listOf("THUDM/GLM-4.1V-9B-Thinking", "Qwen/Qwen2.5-VL-7B-Instruct"),
        cost = "部分模型免费",
        free = true,
    ),
    ProviderPreset(
        name = "通义千问",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/",
        intro = "阿里云百炼。新用户一般有免费额度，用完后按量付费。",
        keyUrl = "https://bailian.console.aliyun.com/cn-beijing/model/settings/api-key",
        chatModels = listOf("qwen-plus", "qwen-flash"),
        visionModels = listOf("qwen3-vl-plus", "qwen-vl-plus"),
        cost = "新用户有额度",
        note = "第一次进去如果提示开通百炼服务，先点开通；创建 Key 时地域保持「华北2（北京）」。",
    ),
    ProviderPreset(
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com/",
        intro = "对话便宜好用，但不能看图，拍照识别要另外配一家（比如智谱）。",
        keyUrl = "https://platform.deepseek.com/api_keys",
        chatModels = listOf("deepseek-flash", "deepseek-v4-flash", "deepseek-chat"),
        cost = "按量付费",
        note = "需要先在平台充值一点余额才能调用。",
    ),
    ProviderPreset(
        name = "OpenAI（ChatGPT）",
        baseUrl = "https://api.openai.com/v1/",
        intro = "ChatGPT 背后的模型。自动配的 gpt-6.1-sol 用来对话，便宜的 gpt-6-luna 用来识别照片。注意 ChatGPT 会员不包含 API，要在开发者平台单独充值。",
        keyUrl = "https://platform.openai.com/api-keys",
        chatModels = listOf("gpt-6.1-sol", "gpt-6-luna"),
        visionModels = listOf("gpt-6-luna", "gpt-6.1-sol"),
        cost = "按量付费",
        china = false,
        note = "需要海外手机号和海外信用卡；创建好的 Key 只显示一次，记得马上复制。",
    ),
    ProviderPreset(
        name = "Anthropic（Claude）",
        baseUrl = "https://api.anthropic.com/v1/",
        intro = "Claude 模型，通过 OpenAI 兼容接口接入。自动配的 claude-sonnet-5-5 用来对话，便宜的 claude-haiku-5-5 用来识别照片。Claude 会员不包含 API，要在开发者平台单独充值。",
        keyUrl = "https://platform.claude.com/settings/keys",
        chatModels = listOf("claude-sonnet-5-5", "claude-haiku-5-5"),
        visionModels = listOf("claude-haiku-5-5", "claude-sonnet-5-5"),
        cost = "按量付费",
        china = false,
        note = "需要海外网络和海外信用卡；创建好的 Key 只显示一次，记得马上复制。",
    ),
    ProviderPreset(
        name = "Google Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
        intro = "对话和看图都行，有免费额度（有次数限制）。",
        keyUrl = "https://aistudio.google.com/apikey",
        chatModels = listOf("gemini-3.8-flash", "gemini-3-flash", "gemini-2.5-flash"),
        visionModels = listOf("gemini-3.8-flash", "gemini-3-flash", "gemini-2.5-flash"),
        cost = "有免费额度",
        free = true,
        china = false,
        note = "用 Google 账号登录。",
    ),
    ProviderPreset(
        name = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1/",
        intro = "一个 Key 用几百个模型。自动配的 openrouter/free 会在免费模型里自动挑，免费的有次数限制；充值后可以换更强的模型。",
        keyUrl = "https://openrouter.ai/settings/keys",
        chatModels = listOf("openrouter/free"),
        visionModels = listOf("openrouter/free"),
        cost = "有免费模型",
        free = true,
        china = false,
    ),
    ProviderPreset(
        name = "Groq",
        baseUrl = "https://api.groq.com/openai/v1/",
        intro = "速度很快，有免费额度，但不能看图，拍照识别要另外配一家。",
        keyUrl = "https://console.groq.com/keys",
        chatModels = listOf("llama-3.3-70b-versatile", "openai/gpt-oss-120b"),
        cost = "有免费额度",
        free = true,
        china = false,
    ),
    ProviderPreset("自定义", "", "任意 OpenAI 兼容接口，自己填接口地址和 Key，再到下面选模型。"),
)

/** 按接口地址找对应的常用服务（编辑已添加的服务时用） */
fun presetFor(baseUrl: String): ProviderPreset? =
    PROVIDER_PRESETS.firstOrNull { it.baseUrl.isNotEmpty() && it.baseUrl.trimEnd('/').equals(baseUrl.trim().trimEnd('/'), ignoreCase = true) }
