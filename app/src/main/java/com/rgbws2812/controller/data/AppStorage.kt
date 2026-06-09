package com.rgbws2812.controller.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
            .put("version", 1)
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

        private fun controlToJson(control: RgbControlState): JSONObject =
            JSONObject()
                .put("mode", control.mode.wireValue)
                .put("red", control.red.coerceIn(0, 255))
                .put("green", control.green.coerceIn(0, 255))
                .put("blue", control.blue.coerceIn(0, 255))
                .put("brightness", control.brightness.coerceIn(0, 255))
                .put("period", control.period.coerceIn(1, 255))
                .put("order", JSONArray(control.order))

        private fun jsonToControl(json: JSONObject): RgbControlState {
            val orderJson = json.optJSONArray("order") ?: JSONArray(RgbControlState.DefaultOrder)
            val order = List(orderJson.length()) { index -> orderJson.optInt(index, -1) }
            return RgbControlState(
                mode = ControlMode.fromWireValue(json.optInt("mode", ControlMode.Flow.wireValue)),
                red = json.optInt("red", 0),
                green = json.optInt("green", 255),
                blue = json.optInt("blue", 0),
                brightness = json.optInt("brightness", 17),
                period = json.optInt("period", 20),
                order = if (order.size == 8) order else RgbControlState.DefaultOrder
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
    }
}
