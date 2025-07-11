package com.example.lrplayer

import android.util.Log
import java.io.InputStream

data class Subtitle(val startTimeMs: Int, val endTimeMs: Int, val text: String)

class Subtitles {

    // NEW constructor for direct InputStream
    constructor(inputStream: InputStream?) {
        if (inputStream != null) {
            subtitles = loadSubtitles(inputStream)
        } else {
            Log.e("Subtitles", "InputStream is null - cannot load subtitles.")
            throw RuntimeException("cannot")
        }
    }

    private fun loadSubtitles(inputStream: InputStream): List<Subtitle> {
        val reader = inputStream.bufferedReader()
        val subtitleList = mutableListOf<Subtitle>()
        var currentStartTimeMs = 0
        var currentEndTimeMs = 0
        val currentText = StringBuilder()

        reader.useLines { lines ->
            lines.forEach { line ->
                when {
                    line.isBlank() -> {
                        subtitleList.add(
                            Subtitle(
                                currentStartTimeMs,
                                currentEndTimeMs,
                                currentText.toString()
                            )
                        )
                        currentText.clear()
                    }

                    line.matches(Regex("\\d+")) -> {
                        // Skip index lines
                    }

                    line.contains(" --> ") -> {
                        val times = line.split(" --> ")
                        currentStartTimeMs = parseSrtTimestamp(times[0])
                        currentEndTimeMs = parseSrtTimestamp(times[1])
                    }

                    else -> {
                        currentText.appendLine(line)
                    }
                }
            }
            // Add the last subtitle if it exists
            if (currentText.isNotEmpty()) {
                subtitleList.add(
                    Subtitle(
                        currentStartTimeMs,
                        currentEndTimeMs,
                        currentText.toString()
                    )
                )
            }
        }
        return subtitleList.sortedBy { it.startTimeMs }
    }

    fun getSubtitleTextByIdx(idx: Int): Pair<Subtitle, Int> {
        val i: Int

        if (idx < 0) {
            i = 0
        } else if (idx >= subtitles.size) {
            i = subtitles.size - 1
        } else {
            i = idx
        }


        val subtitle = subtitles[i]
        return Pair(subtitle, i)
    }

    fun getSubtitleTextByTimestamp(currentPlaybackMs: Int): Pair<Subtitle, Int> {
        var idx = bSearch(currentPlaybackMs)

        if(idx >= 0) {
            val subtitle: Subtitle = subtitles[idx]
            return Pair(subtitle, idx)
        } else {
            return Pair(Subtitle(-1, -1, "_"), idx)
        }
    }


    private lateinit var subtitles: List<Subtitle>
    var lastNextPrevTs:Long = 0;

    fun getNextSubtitle(currentPlaybackMs: Int, forward: Boolean): Triple<Int, String, Int> {
        val now = System.currentTimeMillis()

        var delta = now - lastNextPrevTs
        lastNextPrevTs = now

        PlaybackStateHelper.currentSubtitleIdxUpdated = true
        if(delta < 1000) {
            var d = if (PlaybackStateHelper.autoClick) 10 else 1
            var nextPos = if (forward) PlaybackStateHelper.currentSubtitleIdx + d else PlaybackStateHelper.currentSubtitleIdx - d

            val (subtitle, idx) = getSubtitleTextByIdx(nextPos)

            PlaybackStateHelper.currentSubtitleIdx = idx
            return Triple(subtitle.startTimeMs, subtitle.text, idx)
        } else {
            var mid = bSearch(currentPlaybackMs)
            val subtitle: Subtitle
            val idx:Int

            if(mid < 0) {
                mid = -mid - 1
                if (forward) {
                    if (mid < subtitles.size) idx = mid else idx = subtitles.size - 1
                } else {
                    if (mid - 1 >= 0) idx = mid - 1 else idx = mid
                }
            } else {
                if (forward) {
                    if (mid + 1 < subtitles.size) idx = mid + 1 else idx = mid
                } else {
                    if (mid - 1 >= 0) idx = mid - 1 else idx = mid
                }
            }

            subtitle = subtitles[idx]
            PlaybackStateHelper.currentSubtitleIdx = idx
            return Triple(subtitle.startTimeMs, subtitle.text, idx)

        }
    }

    private fun bSearch(currentPlaybackMs: Int): Int {
        var low = 0
        var high = subtitles.size - 1

        //Log.d("Subtitle", " __________ currentPlaybackMs: $currentPlaybackMs ${subtitles.size}")

        if (currentPlaybackMs < subtitles[0].startTimeMs) {
            return -1 - 0
        } else if (currentPlaybackMs > subtitles[subtitles.size - 1].endTimeMs) {
            return -1 -(subtitles.size)
        }

        while (low <= high) {
            val mid = (low + high) / 2
            val subtitle = subtitles[mid]

            //Log.d("Subtitle", " __________ currentPlaybackMs 2: $low $mid $high ${subtitle.startTimeMs} ${subtitle.endTimeMs} $currentPlaybackMs")
            when {
                subtitle.startTimeMs <= currentPlaybackMs && currentPlaybackMs <= subtitle.endTimeMs -> {
                    val idx: Int = mid
                    return idx
                }

                currentPlaybackMs < subtitle.startTimeMs -> high = mid - 1
                else -> low = mid + 1
            }
        }
        return -1 - low
        //throw RuntimeException("cannot happen!")
    }

    private fun parseSrtTimestamp(timestamp: String): Int {
        val parts = timestamp.split(":", ",")
        val hours = parts[0].toInt()
        val minutes = parts[1].toInt()
        val seconds = parts[2].toInt()
        val milliseconds = parts[3].toInt()
        return (hours * 3600 + minutes * 60 + seconds) * 1000 + milliseconds
    }

    fun getLength(): Int {
        return subtitles.size;
    }

}