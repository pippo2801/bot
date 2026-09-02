package com.amazego.autosolver.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * AccessibilityService utilizzato dal bot per eseguire tap
 * sullo schermo senza root e senza ADB.
 */
class AccessibilityController : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityController"

        @Volatile
        var instance: AccessibilityController? = null
            private set

        val isServiceEnabled: Boolean
            get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        instance = this

        Log.i(
            TAG,
            "ACCESSIBILITY CONNECTED - servizio attivo"
        )

        Log.i(
            TAG,
            "serviceInfo=${serviceInfo}"
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Non è necessario elaborare gli eventi per i tap.
    }

    override fun onInterrupt() {
        Log.w(TAG, "ACCESSIBILITY INTERRUPTED")
        instance = null
    }

    override fun onDestroy() {
        Log.w(TAG, "ACCESSIBILITY DESTROYED")

        if (instance === this) {
            instance = null
        }

        super.onDestroy()
    }

    /**
     * Esegue un tap sullo schermo alle coordinate indicate.
     *
     * @param x coordinata X in pixel
     * @param y coordinata Y in pixel
     * @param durationMs durata del tocco
     *
     * @return true se Android accetta e completa il gesto.
     */
    suspend fun performTap(
        x: Float,
        y: Float,
        durationMs: Long = 60L
    ): Boolean = suspendCancellableCoroutine { continuation ->

        Log.d(
            TAG,
            "Richiesto TAP: x=$x y=$y duration=$durationMs"
        )

        val path = Path().apply {
            moveTo(x, y)
        }

        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            durationMs
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        val callback = object : GestureResultCallback() {

            override fun onCompleted(
                gestureDescription: GestureDescription?
            ) {
                super.onCompleted(gestureDescription)

                Log.i(
                    TAG,
                    "TAP COMPLETATO: x=$x y=$y"
                )

                if (continuation.isActive) {
                    continuation.resume(true)
                }
            }

            override fun onCancelled(
                gestureDescription: GestureDescription?
            ) {
                super.onCancelled(gestureDescription)

                Log.e(
                    TAG,
                    "TAP CANCELLATO: x=$x y=$y"
                )

                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }

        try {
            val dispatched = dispatchGesture(
                gesture,
                callback,
                null
            )

            Log.d(
                TAG,
                "dispatchGesture risultato=$dispatched"
            )

            if (!dispatched && continuation.isActive) {
                continuation.resume(false)
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "ERRORE dispatchGesture: ${e.message}",
                e
            )

            if (continuation.isActive) {
                continuation.resume(false)
            }
        }

        continuation.invokeOnCancellation {
            Log.w(
                TAG,
                "performTap cancellato: x=$x y=$y"
            )
        }
    }
}
