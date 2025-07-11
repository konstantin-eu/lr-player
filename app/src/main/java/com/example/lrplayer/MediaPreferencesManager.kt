package com.example.lrplayer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject

class MediaPreferencesManager(context: Context) {

    //private val sharedPreferences = context.getSharedPreferences("lr_player", Context.MODE_PRIVATE)

    // Key used for saving and retrieving the map
    private val mapKey = "recent_files"

    /**
     * Persists a map of media entries into SharedPreferences.
     *
     * @param mediaMap The map to persist. The map has a filename as the key and a map as the value.
     * The value map contains "mediaUri", "subtitleUri", "mediaFilename", and "currentSubtitlePosition".
     */
    fun persistMediaMap(sharedPreferences: SharedPreferences, mediaMap: HashMap<String, HashMap<String, String>>) {
        val jsonObject = JSONObject()
        for ((filename, attributes) in mediaMap) {
            val attributeJson = JSONObject()
            attributes.forEach { (key, value) ->
                attributeJson.put(key, value.toString())
            }
            jsonObject.put(filename, attributeJson)
        }
        val mapStr = jsonObject.toString()
        Log.d("", "persistMediaMap: $mapKey $mapStr")
        sharedPreferences.edit().putString(mapKey, mapStr).apply()
    }

    /**
     * Restores the map of media entries from SharedPreferences.
     *
     * @return The restored map or an empty map if no data exists.
     */
    fun restoreMediaMap(sharedPreferences: SharedPreferences): HashMap<String, HashMap<String, String>> {
        val jsonString = sharedPreferences.getString(mapKey, null) ?: return HashMap()
        val jsonObject = JSONObject(jsonString)
        val resultMap = HashMap<String, HashMap<String, String>>()

        jsonObject.keys().forEach { filename ->
            val attributeJson = jsonObject.getJSONObject(filename)
            val attributes = HashMap<String, String>()

            attributeJson.keys().forEach { key ->
                val value = attributeJson.getString(key)
                //attributes[key] = if (key == "currentSubtitlePosition") value.toInt() else value
                attributes[key] = value
            }

            resultMap[filename] = attributes
        }
        return resultMap
    }

    /**
     * Clears the stored map from SharedPreferences.
     */
    fun clearMediaMap(sharedPreferences: SharedPreferences,) {
        sharedPreferences.edit().remove(mapKey).apply()
    }
}
