package com.amazego.autosolver

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.amazego.autosolver.databinding.ActivityMainBinding
import com.amazego.autosolver.service.AccessibilityController
import com.amazego.autosolver.service.BotService
import com.amazego.autosolver.service.ScreenCaptureManager
import com.amazego.autosolver.settings.SettingsManager
import com.amazego.autosolver.utils.DebugLogger

/**
 * Schermata principale e pannello di controllo dell'applicazione Amaze Go Auto Solver.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsManager: SettingsManager
    private var screenCaptureManager: ScreenCaptureManager? = null

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            screenCaptureManager?.release()
            val captureManager = ScreenCaptureManager(this).apply {
                initializeProjection(result.resultCode, result.data!!)
            }
            screenCaptureManager = captureManager
            BotService.screenCaptureManager = captureManager

            DebugLogger.log("✓ Proiezione schermo autorizzata con successo.")
            binding.tvStatus.text = getString(R.string.status_idle)
            Toast.makeText(this, "Schermo connesso! Pronto per l'automazione.", Toast.LENGTH_SHORT).show()
        } else {
            DebugLogger.log("✕ Autorizzazione cattura schermo rifiutata.")
            Toast.makeText(this, "Permesso di acquisizione schermo negato.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)
        setupUI()
        observeLogs()
    }

    private fun setupUI() {
        binding.btnConnectScreen.setOnClickListener {
            requestMediaProjectionPermission()
        }

        binding.btnStartBot.setOnClickListener {
            if (!checkAccessibilityPermission()) {
                requestAccessibilityPermission()
                return@setOnClickListener
            }
            if (screenCaptureManager == null || !screenCaptureManager!!.isReady()) {
                Toast.makeText(this, "Connetti prima lo schermo!", Toast.LENGTH_SHORT).show()
                requestMediaProjectionPermission()
                return@setOnClickListener
            }

            val intent = Intent(this, BotService::class.java).apply {
                action = BotService.ACTION_START_BOT
            }
            startService(intent)
        }

        binding.btnStopBot.setOnClickListener {
            val intent = Intent(this, BotService::class.java).apply {
                action = BotService.ACTION_STOP_BOT
            }
            startService(intent)
        }

        binding.btnAnalyzeLevel.setOnClickListener {
            val intent = Intent(this, BotService::class.java).apply {
                action = BotService.ACTION_SOLVE_ONLY
            }
            startService(intent)
        }

        binding.btnSolveOnly.setOnClickListener {
            val intent = Intent(this, BotService::class.java).apply {
                action = BotService.ACTION_SOLVE_ONLY
            }
            startService(intent)
        }

        binding.btnOneStep.setOnClickListener {
            if (!checkAccessibilityPermission()) {
                requestAccessibilityPermission()
                return@setOnClickListener
            }
            val intent = Intent(this, BotService::class.java).apply {
                action = BotService.ACTION_ONE_STEP
            }
            startService(intent)
        }

        binding.btnCalibrate.setOnClickListener {
            Toast.makeText(this, "Apri Amaze GO! e tocca i 4 angoli del tabellone per calibrare.", Toast.LENGTH_LONG).show()
        }

        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        binding.btnImportScreenshot.setOnClickListener {
            Toast.makeText(this, "Seleziona uno screenshot dalla galleria da analizzare.", Toast.LENGTH_SHORT).show()
        }

        checkOverlayPermission()
    }

    private fun requestMediaProjectionPermission() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun checkAccessibilityPermission(): Boolean {
        return AccessibilityController.isServiceEnabled
    }

    private fun requestAccessibilityPermission() {
        Toast.makeText(this, "Abilita 'Amaze Go Auto Solver' nelle impostazioni di Accessibilità", Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun showSettingsDialog() {
        val msg = "Ritardo Tap: ${settingsManager.tapDelayMs}ms\n" +
                "Ritardo Verifica: ${settingsManager.verificationDelayMs}ms\n" +
                "Confidenza minima: ${(settingsManager.minConfidenceThreshold * 100).toInt()}%\n" +
                "Ricalcolo automatico: ${if (settingsManager.isAutoRecalculationEnabled) "ON" else "OFF"}\n" +
                "Auto Next Level: ${if (settingsManager.isAutoNextLevelEnabled) "ON" else "OFF"}"

        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun observeLogs() {
        DebugLogger.addListener { logLine ->
            runOnUiThread {
                val currentText = binding.tvLogs.text.toString()
                binding.tvLogs.text = "$logLine\n$currentText".take(1500)
            }
        }
    }
}
