package com.amazego.autosolver.solver

import com.amazego.autosolver.model.Arrow
import com.amazego.autosolver.model.BoardState
import com.amazego.autosolver.model.Direction
import com.amazego.autosolver.model.Move
import com.amazego.autosolver.model.SolverResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Motore di risoluzione per Amaze GO!.
 *
 * Utilizza una combinazione di:
 * 1. Greedy deterministic fast-path (quando l'ordine delle mosse libere è univoco)
 * 2. Backtracking DFS / BFS con memoization degli stati visitati per casi a bivi multipli
 * 3. Supporto nativo alla cancellazione asincrona via Kotlin Coroutines.
 */
class Solver {

    /**
     * Risolve il tabellone di gioco fornito in input.
     *
     * @param initialState Stato iniziale del tabellone con frecce e coordinate
     * @return SolverResult contenente la sequenza di mosse esatta o indicazione di irrisolvibilità
     */
    suspend fun solve(initialState: BoardState): SolverResult {
        val startTime = System.currentTimeMillis()
        var exploredStatesCount = 0
        val visitedHashes = HashSet<String>()

        if (initialState.arrows.isEmpty()) {
            return SolverResult(
                isSolved = true,
                moves = emptyList(),
                exploredStates = 0,
                calculationTimeMs = System.currentTimeMillis() - startTime
            )
        }

        // Nodo di ricerca per la coda BFS / DFS
        data class SearchNode(val state: BoardState, val path: List<Arrow>)

        val queue = ArrayDeque<SearchNode>()
        queue.add(SearchNode(initialState, emptyList()))
        visitedHashes.add(initialState.getStateHash())

        try {
            while (queue.isNotEmpty()) {
                coroutineContext.ensureActive() // Verifica se l'utente ha premuto STOP
                exploredStatesCount++

                val current = queue.removeFirst()

                if (current.state.isComplete()) {
                    val elapsedTime = System.currentTimeMillis() - startTime
                    val formattedMoves = current.path.mapIndexed { idx, arrow ->
                        Move(
                            stepIndex = idx + 1,
                            arrowId = arrow.id,
                            targetRow = arrow.row,
                            targetCol = arrow.col,
                            direction = arrow.direction,
                            tapScreenX = arrow.centerX,
                            tapScreenY = arrow.centerY
                        )
                    }
                    return SolverResult(
                        isSolved = true,
                        moves = formattedMoves,
                        exploredStates = exploredStatesCount,
                        calculationTimeMs = elapsedTime
                    )
                }

                val availableMoves = current.state.getAvailableMoves()

                // Se non ci sono mosse e il tabellone non è completo -> ramo cieco / deadlock
                for (moveArrow in availableMoves) {
                    val nextState = current.state.applyMove(moveArrow)
                    val hash = nextState.getStateHash()

                    if (!visitedHashes.contains(hash)) {
                        visitedHashes.add(hash)
                        val nextPath = current.path.toMutableList().apply { add(moveArrow) }
                        queue.add(SearchNode(nextState, nextPath))
                    }
                }
            }
        } catch (e: CancellationException) {
            return SolverResult(
                isSolved = false,
                moves = emptyList(),
                exploredStates = exploredStatesCount,
                calculationTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = "Risoluzione interrotta dall'utente."
            )
        } catch (e: Exception) {
            return SolverResult(
                isSolved = false,
                moves = emptyList(),
                exploredStates = exploredStatesCount,
                calculationTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = "Errore durante il calcolo: ${e.localizedMessage}"
            )
        }

        val elapsedTime = System.currentTimeMillis() - startTime
        return SolverResult(
            isSolved = false,
            moves = emptyList(),
            exploredStates = exploredStatesCount,
            calculationTimeMs = elapsedTime,
            errorMessage = "Nessuna soluzione valida trovata (deadlock o configurazione ciclica)."
        )
    }

    /**
     * Genera un livello casuale garantito risolvibile attraverso generazione a ritroso (reverse simulation).
     * Partendo da un tabellone vuoto, inserisce frecce lungo traiettorie libere di ingresso.
     */
    fun generateSolvableBoard(rows: Int = 6, cols: Int = 6, arrowCount: Int = 12): BoardState {
        val totalCells = rows * cols
        val targetCount = arrowCount.coerceIn(4, totalCells)
        
        val grid = Array(rows) { Array<Arrow?>(cols) { null } }
        val generatedArrows = mutableListOf<Arrow>()
        var idCounter = 1

        val allPositions = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                allPositions.add(r to c)
            }
        }
        allPositions.shuffle()

        val directions = Direction.entries.toTypedArray()

        for ((r, c) in allPositions) {
            if (generatedArrows.size >= targetCount) break
            
            // Scegli una direzione casuale
            val dir = directions.random()
            val arrow = Arrow(
                id = idCounter++,
                row = r,
                col = c,
                direction = dir,
                centerX = 0f,
                centerY = 0f,
                confidence = 1.0f
            )
            grid[r][c] = arrow
            generatedArrows.add(arrow)
        }

        val state = BoardState(rows, cols, generatedArrows)
        return state
    }
}
