# Amaze Go Auto Solver — Android Autonomous Bot & CV Engine

Applicazione Android nativa completa in Kotlin per la risoluzione autonoma in tempo reale dei livelli del gioco **Amaze GO!**.

## 🌟 Caratteristiche Principali

- **Offline & Locale al 100%**: Nessuna chiamata cloud o API esterna. Funziona a latenza zero direttamente sul dispositivo.
- **Computer Vision Locale**: Riconoscimento automatico dei confini del tabellone, matrice di griglia e orientamento delle frecce (UP, DOWN, LEFT, RIGHT) tramite baricentro e densità pixel asimmetrica.
- **Motore Solver Deterministico**: Algoritmo BFS/Backtracking con memoization degli stati ed eliminazione dei cicli di deadlock.
- **Automazione Sicura Non-Root**: Utilizza l'API `AccessibilityService` ufficiale di Android (`dispatchGesture`) senza richiedere sblocco root o ADB.
- **Cattura Fotogrammi Ottimizzata**: Integrazione `MediaProjection` con acquisizione *on-demand* (nessun consumo continuo a 60 FPS).
- **Verifica Post-Mossa con Ricalcolo Dinamico**: Verifica dopo ogni singolo tap se la freccia è scomparsa. In caso di discrepanze rileva nuovamente il tabellone e ricalcola la sequenza.
- **Floating Overlay Bubble**: Controlli rapidi e telemetria in tempo reale sopra la schermata di gioco.

---

## 📁 Architettura del Progetto

```
android_project/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/amazego/autosolver/
│       │   │   ├── MainActivity.kt
│       │   │   ├── model/
│       │   │   │   ├── Direction.kt
│       │   │   │   ├── Arrow.kt
│       │   │   │   ├── Move.kt
│       │   │   │   ├── BoardState.kt
│       │   │   │   └── SolverResult.kt
│       │   │   ├── solver/
│       │   │   │   └── Solver.kt
│       │   │   ├── cv/
│       │   │   │   ├── BoardDetector.kt
│       │   │   │   ├── GridAnalyzer.kt
│       │   │   │   ├── ArrowDetector.kt
│       │   │   │   └── GameDetector.kt
│       │   │   ├── service/
│       │   │   │   ├── ScreenCaptureManager.kt
│       │   │   │   ├── AccessibilityController.kt
│       │   │   │   └── BotService.kt
│       │   │   ├── verifier/
│       │   │   │   └── GameStateVerifier.kt
│       │   │   ├── mapper/
│       │   │   │   └── CoordinateMapper.kt
│       │   │   ├── settings/
│       │   │   │   └── SettingsManager.kt
│       │   │   └── utils/
│       │   │       └── DebugLogger.kt
│       │   └── res/
│       │       ├── layout/ (activity_main.xml, overlay_bot_bubble.xml)
│       │       ├── xml/ (accessibility_service_config.xml)
│       │       └── values/ (strings.xml, colors.xml, themes.xml)
│       └── test/java/com/amazego/autosolver/
│           ├── SolverUnitTest.kt
│           ├── CoordinateMapperTest.kt
│           └── BoardStateTest.kt
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🛠️ Guida alla Compilazione con Android Studio

1. **Requisiti**:
   - Android Studio Hedgehog (2023.1.1) o superiore
   - Android SDK 34 (Android 14)
   - JDK 17 o superiore

2. **Apertura Progetto**:
   - Apri Android Studio -> `File` -> `Open...` -> Seleziona la cartella `android_project`
   - Attendi il completamento della sincronizzazione Gradle (`Sync Project with Gradle Files`).

3. **Compilazione via Terminale**:
   ```bash
   ./gradlew assembleDebug
   ```
   L'APK generato sarà presente in:
   `app/build/outputs/apk/debug/app-debug.apk`

4. **Esecuzione dei Test Unitari**:
   ```bash
   ./gradlew test
   ```

---

## 📱 Guida all'Utilizzo sul Dispositivo

1. **Installazione**: Installa l'APK compilato sul tuo telefono Android.
2. **Abilitazione Permessi**:
   - **Accessibilità**: Vai in *Impostazioni Android* > *Accessibilità* > *App installate* > Abilita **Amaze Go Auto Solver**.
   - **Sovrapposizione su altre app**: Concedi il permesso di visualizzazione in sovrimpressione.
   - **Cattura Schermo**: All'avvio dell'app premi `CONNETTI SCHERMO` e accetta il prompt di sistema di MediaProjection.
3. **Avvio Bot**:
   - Apri il gioco **Amaze GO!** sul tuo livello.
   - Dalla bolla floating o dall'app premi **AVVIA BOT** (oppure **ESEGUI UNA SOLA MOSSA** per test controllati).
   - Per fermare in qualunque momento tocca il pulsante rosso **STOP**.
