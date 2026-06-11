package com.rgbws2812.controller.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rgbws2812.controller.audio.MusicReactiveAudioSource
import com.rgbws2812.controller.audio.MusicReactiveDetectionMode
import com.rgbws2812.controller.audio.MusicReactiveSettings
import com.rgbws2812.controller.model.AppStorageState
import com.rgbws2812.controller.model.ControlMode
import com.rgbws2812.controller.model.Preset
import com.rgbws2812.controller.model.RgbControlState
import com.rgbws2812.controller.model.SendHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.appDataStore by preferencesDataStore(name = "rgb_controller")

class AppStorage(
    context: Context
) {
    private val dataStore = context.applicationContext.appDataStore

    val state: Flow<AppStorageState> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { preferences ->
            AppStorageState(
                control = preferences[ControlKey]?.let { decodeControl(it) } ?: RgbControlState.Default,
                autoSendEnabled = preferences[AutoSendKey] ?: false,
                showAdvancedSendPanel = preferences[ShowAdvancedSendPanelKey] ?: false,
                useAdvancedFlowEditor = preferences[UseAdvancedFlowEditorKey] ?: false,
                musicSettings = preferences[MusicSettingsKey]?.let { decodeMusicSettings(it) } ?: MusicReactiveSettings(),
                presets = preferences[PresetsKey]?.let { decodePresets(it) } ?: emptyList(),
                history = preferences[HistoryKey]?.let { decodeHistory(it) } ?: emptyList()
            )
        }

    suspend fun saveControl(control: RgbControlState) {
        dataStore.edit { preferences ->
            preferences[ControlKey] = encodeControl(control)
        }
    }

    suspend fun saveAutoSend(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AutoSendKey] = enabled
        }
    }

    suspend fun saveShowAdvancedSendPanel(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[ShowAdvancedSendPanelKey] = enabled
        }
    }

    suspend fun saveUseAdvancedFlowEditor(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UseAdvancedFlowEditorKey] = enabled
        }
    }

    suspend fun saveMusicSettings(settings: MusicReactiveSettings) {
        dataStore.edit { preferences ->
            preferences[MusicSettingsKey] = encodeMusicSettings(settings)
        }
    }

    suspend fun savePresets(presets: List<Preset>) {
        dataStore.edit { preferences ->
            preferences[PresetsKey] = encodePresets(presets)
        }
    }

    suspend fun saveHistory(history: List<SendHistoryItem>) {
        dataStore.edit { preferences ->
            preferences[HistoryKey] = encodeHistory(history)
        }
    }

    suspend fun replacePortableData(presets: List<Preset>, history: List<SendHistoryItem>) {
        dataStore.edit { preferences ->
            preferences[PresetsKey] = encodePresets(presets)
            preferences[HistoryKey] = encodeHistory(history)
        }
    }

    fun exportPortableData(presets: List<Preset>, history: List<SendHistoryItem>): String =
        JSONObject()
            .put("version", 2)
            .put("presets", presetsToJson(presets))
            .put("history", historyToJson(history))
            .toString(2)

    fun importPortableData(json: String): Pair<List<Preset>, List<SendHistoryItem>> {
        val root = JSONObject(json)
        val presets = jsonToPresets(root.optJSONArray("presets") ?: JSONArray())
        val history = jsonToHistory(root.optJSONArray("history") ?: JSONArray())
        return presets to history
    }

    companion object {
        private val ControlKey = stringPreferencesKey("control")
        private val AutoSendKey = booleanPreferencesKey("auto_send")
        private val ShowAdvancedSendPanelKey = booleanPreferencesKey("show_advanced_send_panel")
        private val UseAdvancedFlowEditorKey = booleanPreferencesKey("use_advanced_flow_editor")
        private val MusicSettingsKey = stringPreferencesKey("music_settings")
        private val PresetsKey = stringPreferencesKey("presets")
        private val HistoryKey = stringPreferencesKey("history")
        const val MaxHistoryItems = 60

        fun encodeControl(control: RgbControlState): String = controlToJson(control).toString()

        fun decodeControl(json: String): RgbControlState = runCatching {
            jsonToControl(JSONObject(json)).clamped()
        }.getOrDefault(RgbControlState.Default)

        fun encodePresets(presets: List<Preset>): String = presetsToJson(presets).toString()

        fun decodePresets(json: String): List<Preset> = runCatching {
            jsonToPresets(JSONArray(json))
        }.getOrDefault(emptyList())

        fun encodeHistory(history: List<SendHistoryItem>): String = historyToJson(history).toString()

        fun decodeHistory(json: String): List<SendHistoryItem> = runCatching {
            jsonToHistory(JSONArray(json))
        }.getOrDefault(emptyList())

        fun encodeMusicSettings(settings: MusicReactiveSettings): String = musicSettingsToJson(settings).toString()

        fun decodeMusicSettings(json: String): MusicReactiveSettings = runCatching {
            jsonToMusicSettings(JSONObject(json))
        }.getOrDefault(MusicReactiveSettings())

        private fun controlToJson(control: RgbControlState): JSONObject =
            JSONObject()
                .put("mode", control.mode.wireValue)
                .put("red", control.red.coerceIn(0, 255))
                .put("green", control.green.coerceIn(0, 255))
                .put("blue", control.blue.coerceIn(0, 255))
                .put("brightness", control.brightness.coerceIn(0, 255))
                .put("flowInterval", control.flowInterval.coerceIn(1, 255))
                .put("breathPeriod", control.breathPeriod.coerceIn(1, 255))
                .put("gradientPeriod", control.gradientPeriod.coerceIn(1, 255))
                .put("order", JSONArray(control.order))
                .put("flowFrames", JSONArray(control.flowFrames.map { it.coerceIn(0, 255) }))

        private fun jsonToControl(json: JSONObject): RgbControlState {
            val orderJson = json.optJSONArray("order")
            val order = if (orderJson == null) {
                RgbControlState.DefaultOrder
            } else {
                List(orderJson.length()) { index -> orderJson.optInt(index, -1) }
            }
            val flowFramesJson = json.optJSONArray("flowFrames")
            val flowFrames = if (flowFramesJson == null) {
                RgbControlState.EmptyFlowFrames
            } else {
                List(flowFramesJson.length()) { index -> flowFramesJson.optInt(index, 0) }
            }
            return restoreControlState(
                modeValue = json.optInt("mode", ControlMode.Flow.wireValue),
                red = json.optInt("red", 0),
                green = json.optInt("green", 255),
                blue = json.optInt("blue", 0),
                brightness = json.optInt("brightness", 17),
                flowInterval = json.optInt("flowInterval", Int.MIN_VALUE),
                breathPeriod = json.optInt("breathPeriod", Int.MIN_VALUE),
                gradientPeriod = json.optInt("gradientPeriod", Int.MIN_VALUE),
                legacyPeriod = json.optInt("period", Int.MIN_VALUE),
                order = order,
                flowFrames = flowFrames
            )
        }

        // 统一新旧存档恢复逻辑，避免 DataStore/JSON/测试各自复制一套默认值规则。
        internal fun restoreControlState(
            modeValue: Int,
            red: Int,
            green: Int,
            blue: Int,
            brightness: Int,
            flowInterval: Int,
            breathPeriod: Int,
            gradientPeriod: Int,
            legacyPeriod: Int,
            order: List<Int>,
            flowFrames: List<Int>
        ): RgbControlState {
            val legacyFallback = legacyPeriod.takeIf { it != Int.MIN_VALUE }
            return RgbControlState(
                mode = ControlMode.fromWireValue(modeValue),
                red = red,
                green = green,
                blue = blue,
                brightness = brightness,
                flowInterval = flowInterval.takeIf { it != Int.MIN_VALUE }
                    ?: legacyFallback
                    ?: RgbControlState.DefaultFlowInterval,
                breathPeriod = breathPeriod.takeIf { it != Int.MIN_VALUE }
                    ?: legacyFallback
                    ?: RgbControlState.DefaultBreathPeriod,
                gradientPeriod = gradientPeriod.takeIf { it != Int.MIN_VALUE }
                    ?: legacyFallback
                    ?: RgbControlState.DefaultGradientPeriod,
                order = order,
                flowFrames = flowFrames
            ).clamped()
        }

        private fun presetsToJson(presets: List<Preset>): JSONArray =
            JSONArray().also { array ->
                presets.forEach { preset ->
                    array.put(
                        JSONObject()
                            .put("id", preset.id)
                            .put("name", preset.name)
                            .put("control", controlToJson(preset.control))
                            .put("createdAt", preset.createdAt)
                            .put("updatedAt", preset.updatedAt)
                    )
                }
            }

        private fun jsonToPresets(array: JSONArray): List<Preset> =
            List(array.length()) { index -> array.optJSONObject(index) }
                .mapNotNull { json ->
                    json?.let {
                        val controlJson = it.optJSONObject("control") ?: return@let null
                        Preset(
                            id = it.optString("id").ifBlank { "preset-${System.currentTimeMillis()}" },
                            name = it.optString("name").ifBlank { "未命名预设" },
                            control = jsonToControl(controlJson),
                            createdAt = it.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = it.optLong("updatedAt", System.currentTimeMillis())
                        )
                    }
                }

        private fun historyToJson(history: List<SendHistoryItem>): JSONArray =
            JSONArray().also { array ->
                history.take(MaxHistoryItems).forEach { item ->
                    array.put(
                        JSONObject()
                            .put("id", item.id)
                            .put("timestamp", item.timestamp)
                            .put("deviceName", item.deviceName)
                            .put("deviceAddress", item.deviceAddress)
                            .put("source", item.source)
                            .put("summary", item.summary)
                            .put("hex", item.hex)
                    )
                }
            }

        private fun jsonToHistory(array: JSONArray): List<SendHistoryItem> =
            List(array.length()) { index -> array.optJSONObject(index) }
                .mapNotNull { json ->
                    json?.let {
                        SendHistoryItem(
                            id = it.optString("id").ifBlank { "history-${System.currentTimeMillis()}" },
                            timestamp = it.optLong("timestamp", System.currentTimeMillis()),
                            deviceName = it.optString("deviceName"),
                            deviceAddress = it.optString("deviceAddress"),
                            source = it.optString("source").ifBlank { "导入" },
                            summary = it.optString("summary"),
                            hex = it.optString("hex")
                        )
                    }
                }
                .filter { it.hex.isNotBlank() }
                .take(MaxHistoryItems)

        private fun musicSettingsToJson(settings: MusicReactiveSettings): JSONObject =
            JSONObject()
                .put("maxBrightness", settings.maxBrightness)
                .put("targetFps", settings.targetFps)
                .put("sensitivity", settings.sensitivity)
                .put("punch", settings.punch)
                .put("ambientLimit", settings.ambientLimit)
                .put("detectionMode", settings.detectionMode.name)
                .put("audioSource", settings.audioSource.name)

        private fun jsonToMusicSettings(json: JSONObject): MusicReactiveSettings {
            val audioSource = runCatching {
                MusicReactiveAudioSource.valueOf(json.optString("audioSource", MusicReactiveAudioSource.Microphone.name))
            }.getOrDefault(MusicReactiveAudioSource.Microphone)
            val detectionMode = runCatching {
                MusicReactiveDetectionMode.valueOf(json.optString("detectionMode", MusicReactiveDetectionMode.LowFrequency.name))
            }.getOrDefault(MusicReactiveDetectionMode.LowFrequency)
            return MusicReactiveSettings(
                maxBrightness = json.optInt("maxBrightness", 64),
                targetFps = json.optInt("targetFps", 20),
                sensitivity = json.optInt("sensitivity", 115),
                punch = json.optInt("punch", 125),
                ambientLimit = json.optInt("ambientLimit", 10),
                detectionMode = detectionMode,
                audioSource = audioSource
            ).clamped()
        }
    }
}
