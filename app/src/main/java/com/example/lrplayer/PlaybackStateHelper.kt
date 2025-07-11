package com.example.lrplayer

import java.util.concurrent.atomic.AtomicBoolean

object PlaybackStateHelper {
    val isForeground: AtomicBoolean = AtomicBoolean(false)
    var fileName: String = ""
    var needInitFromSaved: AtomicBoolean = AtomicBoolean(true)

    // Unique channel & notification IDs
    const val CHANNEL_ID = "AUDIO_PLAYBACK_CHANNEL"
    const val NOTIFICATION_ID = 1234


    var forwardButtonAutoclick: Boolean = false
    var autoClick: Boolean = false
    val backgroundServiceStarted: AtomicBoolean = AtomicBoolean(false)

    //@Volatile var currentPosition: Int = 0
    var currentSubtitleIdxUpdated: Boolean = false
    var currentSubtitleIdx: Int = 0
    var prevSubtitleIdx: Int = -1


    //val controlToggle: AtomicBoolean = AtomicBoolean(false)

//    fun updatePosition(position: Int) {
//        currentPosition = position
//    }
}