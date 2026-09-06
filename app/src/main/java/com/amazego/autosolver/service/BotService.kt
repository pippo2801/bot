package com.amazego.autosolver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.amazego.autosolver.MainActivity
import com.amazego.autosolver.R
import com.amazego.autosolver.cv.GameDetector
import com.amazego.autosolver.model.BoardState
import com.amazego.autosolver.model.Move
import com.amazego.autosolver.model.SolverResult
import com.amazego.autosolver.mapper.CoordinateMapper
import com.amazego.autosolver.settings.SettingsManager
import com.amazego.autosolver.solver.Solver
import com.amazego.autosolver.utils.DebugLogger
import com.amazego.autosolver.verifier.GameStateVerifier
import com.amazego.autosolver.verifier.VerificationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

/**
 * Stati della macchina a stati del Bot.
 */
enum class BotState {
    IDLE,
    CAPTURING,
    DETECTING,
    ANALYZING,
    SOLVING,
    READY_TO_EXECUTE,
    EXECUTING,
    VERIFYING,
    LEVEL_COMPLETED,
    NEXT_LEVEL,
    ERROR,
    STOPPED
}

/**
 * Foreground Service principale per l'automazione autonoma di Amaze GO!.
 */
class BotService : Service() {

    companion object {
        const val ACTION_START_BOT = "com.amazego.autosolver.START_BOT"
        const val ACTION_STOP_BOT = "com.amazego.autosolver.STOP_BOT"
        const val ACTION_ONE_STEP = "com.amazego.autosolver.ONE_STEP"
        const val ACTION_SOLVE_ONLY = "com.amazego.autosolver.SOLVE_ONLY"

        var currentState: BotState = BotState.IDLE
            private set

        var screenCaptureManager: ScreenCaptureManager? = null
        var customCalibratedBounds: Rect? = null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var botJob: Job? = null

    private lateinit var settingsManager: SettingsManager
    private val gameDetector = GameDetector()
    private val solver = Solver()
    private val verifier = GameStateVerifier(gameDetector)
    private val coordinateMapper = CoordinateMapper()

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        createNotificationChannel()
        startForeground(1001, createNotification("Bot in standby"))

        if (settingsManager.isDebugOverlayEnabled && android.provider.Settings.canDrawOverlays(this)) {
            showFloatingOverlay()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BOT -> startAutonomousLoop()
            ACTION_STOP_BOT -> stopBot()
            ACTION_ONE_STEP -> executeSingleStep()
            ACTION_SOLVE_ONLY -> analyzeAndSolveOnly()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateState(newState: BotState, logMsg: String? = null) {
        currentState = newState
        logMsg?.let { DebugLogger.log(it) }
        updateOverlayUI(newState)
    }

    /**
     * Ciclo autonomo completo del Bot.
     */
    private fun startAutonomousLoop() {
        stopBot() // Cancella eventuali job precedenti

        botJob = serviceScope.launch {
            updateState(BotState.CAPTURING, "▶ Avvio ciclo autonomo del bot.")
            var completedLevelsCount = 0

            while (currentState != BotState.STOPPED && (completedLevelsCount < settingsManager.maxAutoLevels || settingsManager.maxAutoLevels == 0)) {
                // 1. Acquisizione fotogramma
                updateState(BotState.CAPTURING, "Acquisizione schermo in corso...")
                val captureManager = screenCaptureManager
                if (captureManager == null || !captureManager.isReady()) {
                    updateState(BotState.ERROR, "ScreenCapture non inizializzato. Connetti prima lo schermo.")
                    break
                }

                val frameBitmap = captureManager.captureCurrentScreen()
                if (frameBitmap == null) {
                    updateState(BotState.ERROR, "Cattura frame fallita (bitmap null). Riprovo...")
                    delay(500)
                    continue
                }

                // 2. Rilevamento del gioco
                updateState(BotState.DETECTING, "Rilevamento tabellone e frecce...")
                val detection = gameDetector.processFrame(frameBitmap, customCalibratedBounds)

                if (!detection.isGameFound) {
                    updateState(BotState.ERROR, "Tabellone non trovato. Verifica che Amaze GO sia aperto.")
                    delay(1000)
                    continue
                }

                if (detection.isLevelCompleted) {
                    updateState(BotState.LEVEL_COMPLETED, "🎉 Livello completato!")

                    if (!settingsManager.isAutoNextLevelEnabled) {
                        break
                    }

                    updateState(
                        BotState.NEXT_LEVEL,
                        "Passaggio automatico al prossimo livello..."
                    )

                    val accessibility = AccessibilityController.instance
                    if (accessibility == null) {
                        updateState(
                            BotState.ERROR,
                            "AccessibilityService disabilitato. Impossibile premere Next."
                        )
                        break
                    }

                    var nextLevelStarted = false

                    // Massimo 2 tentativi per evitare di rimanere bloccati
                    // sulla schermata di completamento.
                    repeat(2) outerAttempt@ { attempt ->
                        if (nextLevelStarted || currentState == BotState.STOPPED) return@outerAttempt

                        delay(if (attempt == 0) 1200L else 700L)

                        val nextPoint = coordinateMapper.getNextLevelButtonCoordinate(
                            detection.boardBounds,
                            frameBitmap.height
                        )

                        DebugLogger.log(
                            "Auto Next tentativo ${attempt + 1}/2: " +
                            "tap a (${nextPoint.x.toInt()}, ${nextPoint.y.toInt()})"
                        )

                        val tapSuccess = accessibility.performTap(
                            nextPoint.x,
                            nextPoint.y
                        )

                        if (!tapSuccess) {
                            DebugLogger.log(
                                "Auto Next: dispatchGesture non riuscito al tentativo ${attempt + 1}."
                            )
                            return@outerAttempt
                        }

                        // Verifica che la schermata di completamento venga abbandonata.
                        // Non contiamo ancora il livello: lo contiamo solo dopo
                        // aver osservato il nuovo stato del gioco.
                        repeat(8) verificationAttempt@ {
                            if (currentState == BotState.STOPPED || nextLevelStarted) {
                                return@verificationAttempt
                            }

                            delay(400L)

                            val verifyBitmap = captureManager.captureCurrentScreen()
                                ?: return@verificationAttempt

                            val verifyDetection = gameDetector.processFrame(
                                verifyBitmap,
                                customCalibratedBounds
                            )

                            if (
                                verifyDetection.isGameFound &&
                                !verifyDetection.isLevelCompleted &&
                                verifyDetection.boardState != null &&
                                verifyDetection.boardState.arrows.isNotEmpty()
                            ) {
                                nextLevelStarted = true
                                DebugLogger.log(
                                    "✓ Nuovo livello rilevato dopo Auto Next."
                                )
                            }
                        }
                    }

                    if (!nextLevelStarted) {
                        updateState(
                            BotState.ERROR,
                            "Auto Next: il nuovo livello non è stato rilevato."
                        )
                        break
                    }

                    completedLevelsCount++
                    DebugLogger.log(
                        "Livelli completati automaticamente: $completedLevelsCount"
                    )

                    continue
                }

                val boardState = detection.boardState
                if (boardState == null || boardState.arrows.isEmpty()) {
                    updateState(BotState.ERROR, "Nessuna freccia identificata.")
                    delay(1000)
                    continue
                }

                if (detection.overallConfidence < settingsManager.minConfidenceThreshold && settingsManager.isSafeModeEnabled) {
                    updateState(BotState.ERROR, "Confidenza insufficiente (${(detection.overallConfidence * 100).toInt()}%). Modalità sicura attiva.")
                    break
                }

                // 3. Risoluzione matematica
                updateState(BotState.SOLVING, "Calcolo sequenza ottimale con Solver...")
                val result: SolverResult = solver.solve(boardState)

                if (!result.isSolved || result.moves.isEmpty()) {
                    updateState(BotState.ERROR, "Solver: nessuna sequenza valida trovata (${result.errorMessage ?: "deadlock"}).")
                    break
                }

                DebugLogger.log("Soluzione calcolata: ${result.moves.size} mosse (${result.calculationTimeMs} ms, ${result.exploredStates} stati).")
                updateState(BotState.EXECUTING, "Inizio esecuzione sequenza di tap...")

                // 4. Esecuzione sequenziale con verifica post-mossa
                var currentBoard = boardState
                var hasExecutionError = false

                for ((index, move) in result.moves.withIndex()) {
                    if (currentState == BotState.STOPPED) break

                    DebugLogger.log("Mossa ${index + 1}/${result.moves.size}: Tap su Freccia #${move.arrowId} (${move.direction.symbol}) a (${move.tapScreenX.toInt()}, ${move.tapScreenY.toInt()})")

                    val accessibility = AccessibilityController.instance
                    if (accessibility == null) {
                        updateState(BotState.ERROR, "AccessibilityService disabilitato. Impossibile eseguire tap.")
                        hasExecutionError = true
                        break
                    }

                    // Esecuzione tap non-root
                    val tapSuccess = withTimeoutOrNull(2_000L) {
                        accessibility.performTap(move.tapScreenX, move.tapScreenY)
                    } ?: false
                    if (!tapSuccess) {
                        updateState(
                            BotState.ERROR,
                            "Tap non riuscito o servizio Accessibilità non responsivo."
                        )
                        hasExecutionError = true
                        break
                    }

                    // Ritardo tra tap
                    delay(settingsManager.tapDelayMs)

                    // 5. Verifica post-mossa
                    updateState(BotState.VERIFYING)
                    delay(settingsManager.verificationDelayMs)

                    val postBitmap = captureManager.captureCurrentScreen()
                    if (postBitmap != null) {
                        val boardForVerification = currentBoard ?: continue
                        val targetArrow = boardForVerification.arrows.find { it.id == move.arrowId }
                        if (targetArrow != null) {
                            val verification = verifier.verifyPostMove(
                                postMoveBitmap = postBitmap,
                                expectedRemovedArrow = targetArrow,
                                previousState = boardForVerification,
                                customBoardBounds = customCalibratedBounds
                            )
                            when (verification) {
                                is VerificationResult.Success -> {
                                    DebugLogger.log("✓ Verifica mossa ${index + 1} OK.")
                                    verification.updatedState?.let { currentBoard = it }
                                }
                                is VerificationResult.LevelCompleted -> {
                                    DebugLogger.log("✓ Livello completato durante la sequenza!")
                                    break
                                }
                                is VerificationResult.Mismatch -> {
                                    DebugLogger.log("⚠ Discrepanza rilevata: ${verification.reason}")
                                    if (settingsManager.isAutoRecalculationEnabled) {
                                        DebugLogger.log("Ricalcolo dinamico attivato...")
                                        break // Rientra nel loop principale per ricalcolare
                                    } else {
                                        updateState(BotState.ERROR, "Discrepanza post-mossa. Fermata di sicurezza.")
                                        hasExecutionError = true
                                        break
                                    }
                                }
                                is VerificationResult.Error -> {
                                    DebugLogger.log("Errore verifica: ${verification.message}")
                                }
                            }
                        }
                    }
                }

                if (hasExecutionError) break
            }

            if (currentState != BotState.ERROR) {
                updateState(BotState.IDLE, "Sessione completata. Bot in attesa.")
            }
        }
    }

    private fun executeSingleStep() {
        serviceScope.launch {
            updateState(BotState.CAPTURING, "Modalità Step: acquisizione frame...")
            val captureManager = screenCaptureManager ?: return@launch
            val bitmap = captureManager.captureCurrentScreen() ?: return@launch

            val detection = gameDetector.processFrame(bitmap, customCalibratedBounds)
            val boardState = detection.boardState ?: return@launch

            val result = solver.solve(boardState)
            if (result.isSolved && result.moves.isNotEmpty()) {
                val firstMove = result.moves.first()
                DebugLogger.log("Step singolo: Mossa 1 -> Freccia #${firstMove.arrowId} (${firstMove.direction.symbol})")

                AccessibilityController.instance?.performTap(firstMove.tapScreenX, firstMove.tapScreenY)
                updateState(BotState.IDLE, "Step singolo eseguito.")
            } else {
                updateState(BotState.ERROR, "Nessuna mossa valida per step singolo.")
            }
        }
    }

    private fun analyzeAndSolveOnly() {
        serviceScope.launch {
            updateState(BotState.ANALYZING, "Analisi senza click in corso...")
            val captureManager = screenCaptureManager ?: return@launch
            val bitmap = captureManager.captureCurrentScreen() ?: return@launch

            val detection = gameDetector.processFrame(bitmap, customCalibratedBounds)
            val board = detection.boardState

            if (board != null) {
                val result = solver.solve(board)
                if (result.isSolved) {
                    val sequence = result.moves.joinToString(" → ") { "${it.arrowId} (${it.direction.symbol})" }
                    DebugLogger.log("Soluzione trovata (nessun tap): $sequence")
                    updateState(BotState.READY_TO_EXECUTE, "Soluzione calcolata (${result.moves.size} mosse).")
                } else {
                    updateState(BotState.ERROR, "Solver: irrisolvibile.")
                }
            } else {
                updateState(BotState.ERROR, "Impossibile estrarre lo stato del tabellone.")
            }
        }
    }

    private fun stopBot() {
        botJob?.cancel()
        botJob = null
        updateState(BotState.STOPPED, "⏹ Bot fermato dall'utente.")
    }

    private fun showFloatingOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_bot_bubble, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 120
        }

        overlayView?.findViewById<Button>(R.id.btnOverlayAction)?.setOnClickListener {
            startAutonomousLoop()
        }

        overlayView?.findViewById<Button>(R.id.btnOverlayStop)?.setOnClickListener {
            stopBot()
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateOverlayUI(state: BotState) {
        val tv = overlayView?.findViewById<TextView>(R.id.tvOverlayStatus)
        tv?.post {
            tv.text = when (state) {
                BotState.IDLE -> "🟢 PRONTO"
                BotState.CAPTURING -> "🟡 CATTURA"
                BotState.DETECTING, BotState.ANALYZING -> "🟡 ANALISI"
                BotState.SOLVING -> "🔵 SOLVER"
                BotState.EXECUTING -> "🟣 ESECUZIONE"
                BotState.VERIFYING -> "🔍 VERIFICA"
                BotState.LEVEL_COMPLETED -> "✅ COMPLETATO"
                BotState.ERROR -> "🔴 ERRORE"
                BotState.STOPPED -> "⏹ STOP"
                else -> state.name
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "amazego_solver_channel",
                "Amaze Go Auto Solver Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "amazego_solver_channel")
            .setContentTitle("Amaze Go Auto Solver")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBot()
        serviceScope.cancel()
        overlayView?.let { windowManager?.removeView(it) }
    }
}
