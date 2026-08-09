package com.six.iot.ui.ai

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.six.iot.R
import com.six.iot.databinding.ActivityAiChatBinding
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString

class AiChatActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AiChatActivity"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private lateinit var binding: ActivityAiChatBinding
    private val messageList = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    // UI state
    private var isVoiceMode = false
    private var isRecording = false
    private var isInCancelZone = false
    private var isFingerOnScreen = false
    private var currentTouchY = 0f
    private val cancelThreshold = 150f

    // Recording variables
    private var audioRecord: AudioRecord? = null
    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient()
    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

    private val handler = Handler(Looper.getMainLooper())

    private var recordingThread: Thread? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupChatList()
        setupInputs()
        setupWebSocket()

        addAiMessage("Hello! I'm your IoT AI assistant. Speak Chinese or English, and I'll help you.")
    }

    private fun setupWebSocket() {
        val request = Request.Builder()
            .url("ws://YOUR_BACKEND_IP:8000/ws/audio") 
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connection established")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runOnUiThread {
                    binding.chatInput.setText(text)
                    updateSendButtonVisibility()
                    if (text.contains("intent")) {
                        addUserMessage(text)
                        processUserRequest(text)
                        binding.chatInput.text.clear()
                        updateSendButtonVisibility()
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
            }
        })
    }

    private fun setupInputs() {
        binding.inputModeBtn.setOnClickListener {
            toggleInputMode()
        }

        binding.sendBtn.setOnClickListener {
            val text = binding.chatInput.text.toString().trim()
            if (text.isNotEmpty()) {
                addUserMessage(text)
                binding.chatInput.text.clear()
                processUserRequest(text)
                updateSendButtonVisibility()
            }
        }

        binding.chatInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSendButtonVisibility()
            }
        })

        binding.voiceInputOverlay.setOnTouchListener { _, event ->
            handleVoiceOverlayTouch(event)
        }
    }

    private fun handleVoiceOverlayTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
                    return true
                }

                isFingerOnScreen = true
                currentTouchY = event.rawY
                isInCancelZone = false

                showVoiceInputPanel()
                startVoiceRecording()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isRecording && isFingerOnScreen) {
                    currentTouchY = event.rawY
                    updateVoiceUIByFingerPosition()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isRecording && isFingerOnScreen) {
                    handleFingerRelease()
                }
                return true
            }
        }
        return true
    }

    private fun showVoiceInputPanel() {
        binding.voiceInputPanel.visibility = View.VISIBLE
        binding.inputCard.alpha = 0f 
        updateVoiceUIForReadyToSend()
    }

    private fun updateVoiceUIByFingerPosition() {
        val location = IntArray(2)
        binding.voiceRecordCard.getLocationOnScreen(location)
        val cardTop = location[1]

        if (currentTouchY < (cardTop - cancelThreshold)) {
            if (!isInCancelZone) {
                isInCancelZone = true
                updateVoiceUIForCancel()
            }
        } else {
            if (isInCancelZone) {
                isInCancelZone = false
                updateVoiceUIForReadyToSend()
            }
        }
    }

    private fun handleFingerRelease() {
        isFingerOnScreen = false

        if (isInCancelZone) {
            cancelVoiceRecording()
            runOnUiThread {
                Toast.makeText(this, "录音已取消", Toast.LENGTH_SHORT).show()
            }
        } else {
            stopVoiceRecording()
        }

        hideVoiceInputPanel()
    }

    private fun hideVoiceInputPanel() {
        binding.voiceInputPanel.visibility = View.GONE
        binding.inputCard.visibility = View.VISIBLE
        binding.inputCard.alpha = 1f
        updateSendButtonVisibility()
    }

    private fun updateVoiceUIForReadyToSend() {
        runOnUiThread {
            binding.voiceHintText.text = getString(R.string.ai_chat_swipe_up_to_cancel)
            binding.voiceHintText.setTextColor(ContextCompat.getColor(this, R.color.color_primary))
            binding.voiceMicIcon.setColorFilter(ContextCompat.getColor(this, R.color.color_primary))
            binding.cancelArea.visibility = View.GONE

            binding.voiceBottomHint.text = getString(R.string.ai_chat_release_to_send)
            binding.voiceBottomHint.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }
    }

    private fun updateVoiceUIForCancel() {
        runOnUiThread {
            binding.voiceHintText.text = getString(R.string.ai_chat_release_to_cancel)
            binding.voiceHintText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            binding.voiceMicIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            binding.cancelArea.visibility = View.VISIBLE

            binding.voiceBottomHint.text = getString(R.string.ai_chat_move_back_to_continue)
            binding.voiceBottomHint.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    private fun toggleInputMode() {
        isVoiceMode = !isVoiceMode
        if (isVoiceMode) {
            binding.chatInput.visibility = View.GONE
            binding.voiceInputOverlay.visibility = View.VISIBLE
            binding.inputModeBtn.setImageResource(R.drawable.ic_keyboard)
            binding.sendBtn.visibility = View.GONE
        } else {
            binding.chatInput.visibility = View.VISIBLE
            binding.voiceInputOverlay.visibility = View.GONE
            binding.inputModeBtn.setImageResource(android.R.drawable.ic_btn_speak_now)
            updateSendButtonVisibility()
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startVoiceRecording() {
        try {
            isRecording = true
            if (bufferSize <= 0) {
                hideVoiceInputPanel()
                return
            }

            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                hideVoiceInputPanel()
                return
            }

            runOnUiThread { binding.voiceWaveform.startAnimation() }
            audioRecord?.startRecording()

            recordingThread = Thread {
                val buffer = ByteArray(bufferSize)
                try {
                    while (isRecording && !Thread.currentThread().isInterrupted) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (read > 0) {
                            try {
                                webSocket?.send(buffer.toByteString(0, read))
                                val amplitude = calculateAmplitude(buffer, read)
                                runOnUiThread { binding.voiceWaveform.updateAmplitude(amplitude) }
                            } catch (e: Exception) { Log.e(TAG, "Recording thread error", e) }
                        } else if (read < 0) break
                    }
                } catch (e: Exception) { Log.e(TAG, "Recording thread error", e) }
            }
            recordingThread?.start()
        } catch (e: Exception) {
            hideVoiceInputPanel()
        }
    }

    private fun calculateAmplitude(buffer: ByteArray, length: Int): Float {
        if (length <= 0) return 0f
        var sum = 0.0
        val sampleCount = length / 2
        for (i in 0 until sampleCount) {
            val index = i * 2
            if (index + 1 >= length) break
            val sample = ((buffer[index + 1].toInt() and 0xFF) shl 8) or (buffer[index].toInt() and 0xFF)
            val normalized = sample / 32768.0
            sum += normalized * normalized
        }
        return if (sampleCount == 0) 0f else Math.sqrt(sum / sampleCount).toFloat()
    }

    private fun stopVoiceRecording() {
        isRecording = false
        isFingerOnScreen = false
        runOnUiThread { binding.voiceWaveform.stopAnimation() }
        recordingThread?.interrupt()
        recordingThread = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) { Log.e(TAG, "Error stopping recording", e) }
        finally { audioRecord = null }
    }

    private fun cancelVoiceRecording() {
        isRecording = false
        isFingerOnScreen = false
        runOnUiThread { 
            binding.voiceWaveform.stopAnimation()
            binding.chatInput.text.clear() 
        }
        recordingThread?.interrupt()
        recordingThread = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) { Log.e(TAG, "Error cancelling recording", e) }
        finally { audioRecord = null }
    }

    private fun updateSendButtonVisibility() {
        val hasText = binding.chatInput.text.isNotEmpty()
        binding.sendBtn.visibility = if (hasText) View.VISIBLE else View.GONE
        if (!isVoiceMode) {
            binding.inputModeBtn.visibility = if (hasText) View.GONE else View.VISIBLE
        }
    }

    private fun processUserRequest(userInput: String) {
        val lowerInput = userInput.lowercase()
        if (lowerInput.contains("开灯") || lowerInput.contains("turn on")) {
            addAiMessage("Opening the light.")
            executeMqttCommand("on")
        } else if (lowerInput.contains("关灯") || lowerInput.contains("turn off")) {
            addAiMessage("Closing the light.")
            executeMqttCommand("off")
        } else {
            addAiMessage("I heard: $userInput. I'm processing your intent...")
        }
    }

    private fun executeMqttCommand(action: String) {
        //if (!isMqttBound) return
        try {
            val guid = "your-device-guid"
            val productId = "your-product-id"
            val url = "ssl://a391en72ie4vj-ats.iot.ap-southeast-1.amazonaws.com:8883"
            val topic = "$productId/$guid/shadow/update"
            val payload = "{\"state\":{\"desired\":{\"light\":\"$action\"}}}"
            //mqttService?.publish(url, topic, payload)
        } catch (e: Exception) { Log.e(TAG, "Failed to send MQTT command", e) }
    }

    private fun addUserMessage(text: String) {
        messageList.add(ChatMessage(text, true))
        chatAdapter.notifyItemInserted(messageList.size - 1)
        binding.chatRecyclerView.scrollToPosition(messageList.size - 1)
    }

    private fun addAiMessage(text: String) {
        messageList.add(ChatMessage(text, false))
        chatAdapter.notifyItemInserted(messageList.size - 1)
        binding.chatRecyclerView.scrollToPosition(messageList.size - 1)
    }

    private fun setupToolbar() {
        val toolbar = binding.titleBar.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
        toolbar.navigationIcon = AppCompatResources.getDrawable(this, R.drawable.ic_arrow_back_ios_24dp)
        toolbar.setNavigationOnClickListener { finish() }
        binding.titleBar.toolbarTitle.text = getString(R.string.ai_assistant_title)

        /*val aiIconView = ImageView(this).apply {
            setImageResource(R.drawable.ic_ai_chat)
            val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics).toInt()
            layoutParams = Toolbar.LayoutParams(size, size).apply {
                gravity = Gravity.START
                marginStart = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
            }
        }
        toolbar.addView(aiIconView)*/
    }

    private fun setupChatList() {
        chatAdapter = ChatAdapter(messageList)
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AiChatActivity)
            adapter = chatAdapter
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showVoiceInputPanel()
            startVoiceRecording()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) stopVoiceRecording()
        try { webSocket?.close(1000, "Activity Destroyed") } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
    }
}

class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userLayout: View = view.findViewById(R.id.user_message_layout)
        val aiLayout: View = view.findViewById(R.id.ai_message_layout)
        val userText: TextView = view.findViewById(R.id.user_message_text)
        val aiText: TextView = view.findViewById(R.id.ai_message_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        if (message.isUser) {
            holder.userLayout.visibility = View.VISIBLE
            holder.aiLayout.visibility = View.GONE
            holder.userText.text = message.text
        } else {
            holder.userLayout.visibility = View.GONE
            holder.aiLayout.visibility = View.VISIBLE
            holder.aiText.text = message.text
        }
    }

    override fun getItemCount() = messages.size
}
