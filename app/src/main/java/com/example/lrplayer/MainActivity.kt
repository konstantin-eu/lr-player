package com.example.lrplayer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.lrplayer.PlaybackStateHelper.CHANNEL_ID
import java.io.InputStream
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private var recentFiles: HashMap<String, HashMap<String, String>> = HashMap()
    private var lr_player: SharedPreferences? = null
    private lateinit var runnable: Runnable
    private lateinit var subtitleTextView: TextView
    private lateinit var middleTextView: TextView
    private var audioService: AudioService? = null
    private var isBound = false
    private var pauseToggle = false

    private val INIT_SUB_TEXT = "_____"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Update UI here
            val pause = intent?.getBooleanExtra("MediaSessionPause", false)
            Log.d("MainActivity", "onReceive: $pause")
            if(pause != null && pause) {
                val pauseButton: Button = findViewById(R.id.pauseButton)
                pauseButton.text = "PLAY"
            } else if (pause != null && pause == false) {
                val pauseButton: Button = findViewById(R.id.pauseButton)
                pauseButton.text = "PAUSE"
            }
        }
    }


    // Service connection to bind to the AudioService
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.d("MainActivity", " onServiceConnected")
            val binder = service as AudioService.LocalBinder
            audioService = binder.getService()
            isBound = true
            // Start updating the timestamp TextView
            updateTimestamp()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.d("MainActivity", "onServiceDisconnected")
            isBound = false
        }
    }

    // Holds the Subtitles instance
    var subtitles: Subtitles? = null

    fun getSub(): Subtitles? {
        return subtitles
    }

    private val handler = Handler(Looper.getMainLooper())

    private fun createNotificationChannel(mainActivity: MainActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Playback Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Channel for audio playback foreground service"
            }

            val notificationManager: NotificationManager =
                mainActivity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private val autoClickRunnable = object : Runnable {
        override fun run() {
            val btn: Button
            if (PlaybackStateHelper.forwardButtonAutoclick) {
                btn = findViewById(R.id.ForwardButton)
            } else {
                btn = findViewById(R.id.BackwardButton)
            }

            btn.performClick() // Simulate a click
            handler.postDelayed(this, clickDelay) // Repeat after a delay
        }
    }
    private var clickDelay = 200L // Delay between clicks in milliseconds

    private fun startAutoClick() {
        handler.post(autoClickRunnable)
        PlaybackStateHelper.autoClick = true
    }

    private fun stopAutoClick() {
        handler.removeCallbacks(autoClickRunnable)
        PlaybackStateHelper.autoClick = false
    }

    var mediaPreferencesManager: MediaPreferencesManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter("audio_service_update"))

        // Force Dark Mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        this.lr_player = getSharedPreferences("lr_player", Context.MODE_PRIVATE)
        mediaPreferencesManager = MediaPreferencesManager(this)

        enableEdgeToEdge()

        createNotificationChannel(this)

        // Start the foreground service (without specifying audio yet)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, AudioService::class.java))

            // (Optional) Request audio focus if desired
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            // Create an AudioFocusRequest instance
            val focusRequest = AudioFocusRequest.Builder(AudioManager.STREAM_MUSIC)
                .build()

            val result = audioManager.requestAudioFocus(focusRequest);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {

                val lr_player_t = getSharedPreferences("lr_player", Context.MODE_PRIVATE)

                if(lr_player_t != null) {

                    recentFiles = mediaPreferencesManager?.restoreMediaMap(lr_player_t) ?: HashMap()
                    Log.d("MainActivity", " recentFiles: $recentFiles")

                    val savedPauseToggle = lr_player_t.getBoolean("pauseToggle", false) // 0 is the default value
                    if (savedPauseToggle != null) {
                        pauseToggle = savedPauseToggle
                    }
                    val fn = lr_player_t.getString("persistedFilename", "")
                    playAudio(fn)

                } else {
                    throw RuntimeException("Cannot happen!")
                }


            } else {
                // Show an alert dialog
                val builder = AlertDialog.Builder(this) // Use "this" if you're in an Activity
                builder.setTitle("Audio Focus Unavailable")
                builder.setMessage("Another app is currently using the audio output. Please try again later.")
                builder.setPositiveButton("OK", null)
                val dialog = builder.create()
                dialog.show()
            }

        } else {
            throw RuntimeException(" not supported!")
            startService(Intent(this, AudioService::class.java))
        }
        bindToAudioService()

        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        subtitleTextView = findViewById(R.id.subtitleTextView) // Get the TextView from your layout
        subtitleTextView.setTextIsSelectable(true)

        middleTextView = findViewById(R.id.middleTextView) // Get the TextView from your layout
        middleTextView.text = INIT_SUB_TEXT

        //val playButton: Button = findViewById(R.id.playButton)
        val pauseButton: Button = findViewById(R.id.pauseButton)
        val forwardButton: Button = findViewById(R.id.ForwardButton)
        val backwardButton: Button = findViewById(R.id.BackwardButton)

        // 1) Pick Audio button
        // Make sure to have a Button with id: pickAudioButton in your layout
        val pickAudioButton: ImageView = findViewById(R.id.menuButton)
        pickAudioButton.setOnClickListener { view ->

            val popupMenu = PopupMenu(this, view)
            popupMenu.menuInflater.inflate(R.menu.audio_picker_menu, popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.open_file -> {
                        pickAudioFile()
                        true
                    }
                    R.id.select_from_list -> {

                        Log.d("MainActivity", " popupMenu recentFiles: $recentFiles")

                        var listMap = LinkedHashMap<String, String>()
                        for(k in recentFiles.keys) {
                            listMap.put(k, k)
                        }

                        // Show the DialogFragment
                        val dialog = RecentFilesDialogFragment.newInstance(listMap)
                        dialog.show(supportFragmentManager, RecentFilesDialogFragment.TAG)
                        true
                    }
                    R.id.clear_cache -> {
                        middleTextView.text = INIT_SUB_TEXT

                        recentFiles.clear()
                        PlaybackStateHelper.currentSubtitleIdx = 0
                        PlaybackStateHelper.fileName = ""

                        lr_player?.let {mediaPreferencesManager?.persistMediaMap(it, recentFiles)}
                        val editor = lr_player?.edit()
                        editor?.putBoolean("pauseToggle", pauseToggle) // Replace someValue with the value you want to save
                        editor?.putString("persistedFilename", PlaybackStateHelper.fileName)
                        editor?.apply() // or editor.commit()


                        val intent = Intent(this, AudioService::class.java).apply {
                            putExtra("ACTION", "PLAY")
                            putExtra("CLEAR_RECENT_FILES", true)
                            putExtra("PAUSE_TOGGLE", pauseToggle)
                        }
                        startForegroundService(intent)
                        true
                    }
                    // ... other menu items
                    else -> false
                }
            }

            popupMenu.show()
        }

        // 2) Pause/Play Button
        pauseButton.setOnClickListener {
            if (PlaybackStateHelper.backgroundServiceStarted.get()) {
                // Start the service with ACTION = "PAUSE"
                val intent = Intent(this, AudioService::class.java).apply {
                    putExtra("ACTION", "PAUSE")
                    pauseToggle = !pauseToggle
                    putExtra("PAUSE_TOGGLE", pauseToggle)
                    pauseButton.text = if (!pauseToggle) "PAUSE" else "PLAY"
                }
                ContextCompat.startForegroundService(this, intent)
                //unbindFromAudioService() // Unbind when you stop playback
            }
        }

        // 3) Forward Button
        forwardButton.setOnClickListener {
            if (PlaybackStateHelper.backgroundServiceStarted.get()) {

                nextPrevButton(true)
            }
        }
        forwardButton.setOnLongClickListener {
            // Start auto-clicking
            PlaybackStateHelper.forwardButtonAutoclick = true
            startAutoClick()
            true // Return true to consume the long click event
        }
        forwardButton.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                stopAutoClick()
            }
            false // Return false to allow other touch events to be processed
        }

        // 4) Backward Button
        backwardButton.setOnClickListener {
            if (PlaybackStateHelper.backgroundServiceStarted.get()) {
                nextPrevButton(false)
            }
        }
        backwardButton.setOnLongClickListener {
            // Start auto-clicking
            PlaybackStateHelper.forwardButtonAutoclick = false
            startAutoClick()
            true // Return true to consume the long click event
        }
        backwardButton.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                stopAutoClick()
            }
            false // Return false to allow other touch events to be processed
        }

        // Check notification-related permissions (if needed)
        val ret1 = ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        val ret2 = ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE)
        val ret3 = ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK)
        if (ret1 != PackageManager.PERMISSION_GRANTED
            || ret2 != PackageManager.PERMISSION_GRANTED
            || ret3 != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission(this)
        }

    }

    fun handleRecentFileSelected(fn: String?) {
        Log.d("MainActivity", "Selected file path from dialog: $fn")

        playAudio(fn)
    }

    fun playAudio(fn: String?) {
        if (fn != null && recentFiles.containsKey(fn) &&
            recentFiles[fn]!!.containsKey("persistedUriAudio") &&
            recentFiles[fn]!!["persistedUriAudio"] != "" &&
            recentFiles[fn]!!.containsKey("persistedUriSubtitle") &&
            recentFiles[fn]!!["persistedUriSubtitle"] != "") {

            val audioUriStr = recentFiles[fn]!!["persistedUriAudio"]!!
            val subUriStr = recentFiles[fn]!!["persistedUriSubtitle"]!!

            val audioUri = Uri.parse(audioUriStr)
            val subUri = Uri.parse(subUriStr)

            var audioTestStream: InputStream? = getInputStreamFromUri(audioUri)
            var subTestStream: InputStream? = getInputStreamFromUri(subUri)

            if (audioTestStream == null || subTestStream == null) {
                // Close streams if any were opened
                audioTestStream?.close()
                subTestStream?.close()

                // Clear recentFiles and persisted data due to permission issues
                recentFiles.clear()
                PlaybackStateHelper.fileName = ""
                lr_player?.let { mediaPreferencesManager?.persistMediaMap(it, recentFiles) }
                val editor = lr_player?.edit()
                editor?.putString("persistedFilename", "")
                editor?.apply()
                Log.d("MainActivity", "Cleared recentFiles due to inability to open persisted file (no permissions)")
                return
            } else {
                // Close audio test stream (service will reopen)
                audioTestStream.close()
                // Use subTestStream for subtitles
                subtitles = Subtitles(subTestStream)
                PlaybackStateHelper.fileName = fn
                val intent = Intent(this, AudioService::class.java).apply {
                    putExtra("ACTION", "PLAY")
                    putExtra("AUDIO_URI", audioUriStr)
                    putExtra("PAUSE_TOGGLE", pauseToggle)
                }
                startForegroundService(intent)
            }
        }
    }


    val REQUEST_FILES = 1234567

    private fun pickAudioFile() {

        val filePickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*" // Accept any file type or limit to specific types if needed
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) // Enable multiple selection
        }
        startActivityForResult(filePickerIntent, REQUEST_FILES)
    }


    // Function to get the file name from the Uri
    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                cursor.moveToFirst()
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_FILES) {
            val clipData = data?.clipData
            val singleUri = data?.data

            val uris = mutableListOf<Uri>()

            if (clipData != null) {
                // Multiple files selected
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            } else if (singleUri != null) {
                // Single file selected
                uris.add(singleUri)
            }

            // Process the selected files
            processSelectedFiles(uris)
        }
    }

    private fun getInputStreamFromUri(uri: Uri): InputStream? {
        return try {
            contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to open InputStream for URI: $uri", e)
            null
        }
    }

    private fun processSelectedFiles(uris: List<Uri>) {
        var mediaUri: Uri? = null
        var subtitleUri: Uri? = null

        for (uri in uris) {
            val fileName = getFileNameFromUri(uri)

            val mimeType = contentResolver.getType(uri)

            when {
                mimeType?.startsWith("audio/") == true -> {
                    mediaUri = uri

                }

                mimeType?.startsWith("application/x-subrip") == true || uri.toString().endsWith(".srt") -> {
                    subtitleUri = uri

                }
            }
        }

        if (mediaUri != null && subtitleUri != null) {
            val fileName1 = getFileNameFromUri(mediaUri)

            if (fileName1 != null) {
                PlaybackStateHelper.fileName = fileName1.substringBeforeLast(".")
            } else {
                throw RuntimeException("Cannot happen!")
            }

            subtitles = Subtitles(getInputStreamFromUri(subtitleUri))

            // Persist URI permissions
            contentResolver.takePersistableUriPermission(mediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(subtitleUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            // lr_player

            var fRecord:HashMap<String, String> = HashMap();
            fRecord.put("persistedUriAudio", mediaUri.toString())
            fRecord.put("persistedUriSubtitle", subtitleUri.toString())

            recentFiles.put(PlaybackStateHelper.fileName, fRecord)
            lr_player?.let {mediaPreferencesManager?.persistMediaMap(it, recentFiles)}

            val editor = lr_player?.edit()
            editor?.putString("persistedFilename", PlaybackStateHelper.fileName)
            editor?.apply() // or editor.commit()


            val intent = Intent(this, AudioService::class.java).apply {
                putExtra("ACTION", "PLAY")
                putExtra("AUDIO_URI", mediaUri.toString()) // Pass the URI as a string
                putExtra("PAUSE_TOGGLE", pauseToggle)

            }
            startForegroundService(intent)

        } else {
            // Handle the case where both files were not selected
            Log.d("MainActivity", " Please select both a media file and a subtitle file")
        }
    }


    private fun requestNotificationPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK,
                Manifest.permission.FOREGROUND_SERVICE,
            ),
            NOTIFICATION_PERMISSION_REQUEST_CODE
        )
    }

    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 101 // Any unique integer

    private fun nextPrevButton(forward: Boolean) {
        if(audioService?.mediaPlayer == null) {
            return
        }

        val nextTs: Int
        val s = getSub()
        if (s != null) {
            val currentPosition = audioService?.mediaPlayer?.currentPosition ?: 0
            val (ts, _, _) = s.getNextSubtitle(currentPosition, forward)
            nextTs = ts.toInt()
        } else {
            nextTs = 0
        }

        val intent = Intent(this, AudioService::class.java).apply {
            putExtra("ACTION", "SEEK_TO")
            putExtra("SEEK_TO_TIME_MS", nextTs) // Forward 30 seconds
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun bindToAudioService() {
        Intent(this, AudioService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
    }

    private fun updateTimestamp() {
        if (audioService?.mediaPlayer == null) {
            return
        }

        if (PlaybackStateHelper.backgroundServiceStarted.get()) {

            if (PlaybackStateHelper.needInitFromSaved.get()) {
                PlaybackStateHelper.needInitFromSaved.set(false)
                this.lr_player = getSharedPreferences("lr_player", Context.MODE_PRIVATE)

                val savedCurrentSubtitleIdx: Int?
                if(PlaybackStateHelper.fileName != null && PlaybackStateHelper.fileName != "") {
                    savedCurrentSubtitleIdx = (recentFiles.get(PlaybackStateHelper.fileName)?.get("currentSubtitleIdx")?: "0").toInt()

                } else {
                    savedCurrentSubtitleIdx = null
                }
                if (savedCurrentSubtitleIdx != null) {
                    PlaybackStateHelper.currentSubtitleIdx = savedCurrentSubtitleIdx

                    val (subtitle, idx) = getSub()?.getSubtitleTextByIdx(PlaybackStateHelper.currentSubtitleIdx) ?: Pair(null, -1)

                    val intent = Intent(this, AudioService::class.java).apply {
                        putExtra("ACTION", "SEEK_TO")
                        putExtra("SEEK_TO_TIME_MS", subtitle?.startTimeMs) // Forward 30 seconds
                    }
                    ContextCompat.startForegroundService(this, intent)
                }
            }

            // This function will be called repeatedly to update the timestamp
            val currentPosition = audioService?.mediaPlayer?.currentPosition ?: 0
            val (subtitle1, idx1) = this.getSub()?.getSubtitleTextByTimestamp(currentPosition) ?: Pair(null, -1)

            var newIdx = idx1
            var text = subtitle1?.text
            var ts = ""

            if (PlaybackStateHelper.currentSubtitleIdxUpdated) {
                if (newIdx == PlaybackStateHelper.currentSubtitleIdx) {
                    PlaybackStateHelper.currentSubtitleIdxUpdated = false
                } else {
                    newIdx = PlaybackStateHelper.currentSubtitleIdx
                    val (subtitle, idx) = getSub()?.getSubtitleTextByIdx(newIdx) ?: Pair(null, -1)
                    text = subtitle?.text

                    val millis = subtitle?.startTimeMs?: 0;
                    val hours: Long = TimeUnit.MILLISECONDS.toHours(millis.toLong())
                    val minutes: Long = TimeUnit.MILLISECONDS.toMinutes(millis.toLong()) % 60
                    val seconds: Long = TimeUnit.MILLISECONDS.toSeconds(millis.toLong()) % 60

                    if(hours > 0) {
                        ts = "" + String.format("%02d:%02d:%02d", hours, minutes, seconds)
                    } else {
                        ts = "" + String.format("%02d:%02d", minutes, seconds)
                    }
                }
            }

            if (newIdx != PlaybackStateHelper.prevSubtitleIdx ||
                INIT_SUB_TEXT == middleTextView.text ||
                middleTextView.text.isBlank()
            ) {
                subtitleTextView.text = text ?: ""
                middleTextView.text = "pos: ${newIdx + 1}/${getSub()?.getLength()} ts: $ts"
                PlaybackStateHelper.prevSubtitleIdx = newIdx
            }

            // Re-schedule update
            // Usually do it every 100ms
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to the service in onStart() to re-establish connection
    }

    override fun onResume() {
        super.onResume()

        val savedPauseToggle = lr_player?.getBoolean("pauseToggle", false) // 0 is the default value
        if (savedPauseToggle != null) {
            pauseToggle = savedPauseToggle
        }

        val pauseButton: Button = findViewById(R.id.pauseButton)
        pauseButton.text = if (!pauseToggle) "PAUSE" else "PLAY"

        this.runnable = object : Runnable {
            override fun run() {
                updateTimestamp()
                handler.postDelayed(this, 100)
            }
        }
        this.runnable.run()

    }

    override fun onStop() {
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(runnable)
        handler.removeCallbacks(autoClickRunnable)

        // lr_player


        if (PlaybackStateHelper.backgroundServiceStarted.get() && PlaybackStateHelper.fileName != null && PlaybackStateHelper.fileName != ""
            && audioService != null && audioService?.mediaPlayer != null
        ) {

            val currentPosition = audioService?.mediaPlayer?.currentPosition ?: 0

            val (subtitle1, idx1) = this.getSub()?.getSubtitleTextByTimestamp(currentPosition) ?: Pair(null, -1)

            recentFiles.get(PlaybackStateHelper.fileName)?.put("currentSubtitleIdx", idx1.toString())
            lr_player?.let {mediaPreferencesManager?.persistMediaMap(it, recentFiles)}
            val editor = lr_player?.edit()
            editor?.putBoolean("pauseToggle", pauseToggle) // Replace someValue with the value you want to save
            editor?.apply() // or editor.commit()
            editor?.commit()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)


        if (isBound) {
            val intent = Intent(this, AudioService::class.java).apply {
                putExtra("ACTION", "STOP")
            }
            ContextCompat.startForegroundService(this, intent)

            unbindService(connection)
            isBound = false
        }

    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Re-apply dark mode when the configuration changes
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        } else {
        }
    }
}