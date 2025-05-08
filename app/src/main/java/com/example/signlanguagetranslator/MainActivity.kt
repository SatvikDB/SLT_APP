package com.example.signlanguagetranslator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import com.example.signlanguagetranslator.databinding.ActivityMainBinding
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var player: ExoPlayer
    private val RECORD_AUDIO_REQUEST_CODE = 101
    private var isListening = false
    private val TAG = "SignLanguageApp"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check for microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST_CODE)
        }

        // Initialize ExoPlayer
        player = ExoPlayer.Builder(this).build()
        binding.videoView.player = player

        // Initialize speech recognizer
        initializeSpeechRecognizer()

        // Set up mic button click listener
        binding.micButton.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }

        // Set up translate button
        binding.translateButton.setOnClickListener {
            val text = binding.recognizedText.text.toString()
            if (text.isNotEmpty()) {
                translateToSignLanguage(text)
            } else {
                Toast.makeText(this, "Please speak something first", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up clear button
        binding.clearButton.setOnClickListener {
            binding.recognizedText.text = ""
            binding.displayText.text = ""
            player.stop()
        }

        // Check if video files exist
        checkVideoFiles()
    }

    private fun checkVideoFiles() {
        val commonWords = listOf("hello", "thank", "you", "please", "help", "yes", "no")
        val alphabet = "abcdefghijklmnopqrstuvwxyz".toList().map { it.toString() }

        val missingWords = mutableListOf<String>()
        val missingLetters = mutableListOf<String>()

        for (word in commonWords) {
            val resourceId = resources.getIdentifier(word, "raw", packageName)
            if (resourceId == 0) {
                missingWords.add(word)
            }
        }

        for (letter in alphabet) {
            val resourceId = resources.getIdentifier(letter, "raw", packageName)
            if (resourceId == 0) {
                missingLetters.add(letter)
            }
        }

        if (missingWords.isNotEmpty()) {
            Log.e(TAG, "Missing word videos: ${missingWords.joinToString()}")
        }

        if (missingLetters.isNotEmpty()) {
            Log.e(TAG, "Missing letter videos: ${missingLetters.joinToString()}")
        }

        // Check if at least one video exists
        if (missingWords.size == commonWords.size && missingLetters.size == alphabet.size) {
            Toast.makeText(this, "No video files found in raw folder!", Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    binding.listeningStatus.text = "Listening!"
                    binding.listeningStatus.visibility = View.VISIBLE
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    stopListening()
                }

                override fun onError(error: Int) {
                    stopListening()
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Unknown error"
                    }
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Speech recognition error: $errorMessage")
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val recognizedText = matches[0].uppercase()
                        binding.recognizedText.text = recognizedText
                        binding.displayText.text = recognizedText
                        Log.d(TAG, "Recognized text: $recognizedText")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        binding.recognizedText.text = matches[0].uppercase()
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else {
            Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Speech recognition not available on this device")
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                isListening = true
                binding.micButton.setImageResource(R.drawable.ic_mic_active)
                binding.listeningStatus.visibility = View.VISIBLE
                Log.d(TAG, "Starting speech recognition")
                speechRecognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST_CODE)
        }
    }

    private fun stopListening() {
        isListening = false
        binding.micButton.setImageResource(R.drawable.ic_mic)
        binding.listeningStatus.visibility = View.GONE
        speechRecognizer.stopListening()
    }

    private fun translateToSignLanguage(text: String) {
        // Split the text into words
        val words = text.split(" ")
        Log.d(TAG, "Translating text: $text")
        Log.d(TAG, "Words to translate: ${words.joinToString()}")

        // Play each word's sign language video sequentially
        playWordsSequentially(words, 0)
    }

    private fun playWordsSequentially(words: List<String>, index: Int) {
        if (index >= words.size) return

        val word = words[index].lowercase()
        val resourceId = getResourceIdForWord(word)
        Log.d(TAG, "Playing word: $word, Resource ID: $resourceId")

        if (resourceId != 0) {
            // Word found in resources
            playVideoFromResource(resourceId) {
                // When video completes, play the next word
                playWordsSequentially(words, index + 1)
            }
        } else {
            // Word not found, spell it letter by letter
            Log.d(TAG, "Word not found, spelling letter by letter: $word")
            spellWordLetterByLetter(word) {
                // When spelling completes, play the next word
                playWordsSequentially(words, index + 1)
            }
        }
    }

    private fun getResourceIdForWord(word: String): Int {
        return resources.getIdentifier(word, "raw", packageName)
    }

    private fun playVideoFromResource(resourceId: Int, onCompletion: () -> Unit) {
        try {
            val uri = Uri.parse("android.resource://$packageName/$resourceId")
            val mediaItem = MediaItem.fromUri(uri)

            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()

            Log.d(TAG, "Playing video from resource ID: $resourceId")

            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        Log.d(TAG, "Video playback completed")
                        onCompletion()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error playing video: ${e.message}")
            Toast.makeText(this, "Error playing video: ${e.message}", Toast.LENGTH_SHORT).show()
            // Skip to next word if there's an error
            onCompletion()
        }
    }

    private fun spellWordLetterByLetter(word: String, onCompletion: () -> Unit) {
        val letters = word.toCharArray()
        Log.d(TAG, "Spelling word letter by letter: $word")
        spellLettersSequentially(letters, 0) {
            onCompletion()
        }
    }

    private fun spellLettersSequentially(letters: CharArray, index: Int, onCompletion: () -> Unit) {
        if (index >= letters.size) {
            onCompletion()
            return
        }

        val letter = letters[index].toString()
        val resourceId = resources.getIdentifier(letter, "raw", packageName)
        Log.d(TAG, "Playing letter: $letter, Resource ID: $resourceId")

        if (resourceId != 0) {
            playVideoFromResource(resourceId) {
                spellLettersSequentially(letters, index + 1, onCompletion)
            }
        } else {
            // Skip if letter not found
            Log.e(TAG, "Letter video not found: $letter")
            spellLettersSequentially(letters, index + 1, onCompletion)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Microphone permission granted")
            } else {
                Toast.makeText(this, "Permission Denied - microphone access is required", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Microphone permission denied")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        player.release()
    }
}