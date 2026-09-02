package com.amazego.autosolver

import com.amazego.autosolver.model.Arrow
import com.amazego.autosolver.model.BoardState
import com.amazego.autosolver.model.Direction
import com.amazego.autosolver.solver.Solver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolverUnitTest {

    private val solver = Solver()

    @Test
    fun solverShouldSolveSimpleLinearBoard() = runBlocking {
        // Due frecce: una rivolta a destra verso il bordo, una rivolta a destra che punta alla prima
        // [ → (id 1, col 0), → (id 2, col 1) ] in una griglia 1x3
        val arrow1 = Arrow(id = 1, row = 0, col = 0, direction = Direction.RIGHT)
        val arrow2 = Arrow(id = 2, row = 0, col = 1, direction = Direction.RIGHT)

        val board = BoardState(rows = 1, cols = 3, arrows = listOf(arrow1, arrow2))

        val result = solver.solve(board)

        assertTrue("Il tabellone deve essere risolvibile", result.isSolved)
        assertEquals("Devono esserci 2 mosse", 2, result.moves.size)
        // La freccia 2 deve uscire prima della freccia 1
        assertEquals("La prima mossa deve essere la freccia 2", 2, result.moves[0].arrowId)
        assertEquals("La seconda mossa deve essere la freccia 1", 1, result.moves[1].arrowId)
    }

    @Test
    fun blockedArrowShouldNotBeMovable() {
        // Freccia in (1, 1) verso l'alto, bloccata da una freccia in (0, 1)
        val arrowBlocked = Arrow(id = 1, row = 1, col = 1, direction = Direction.UP)
        val arrowBlocker = Arrow(id = 2, row = 0, col = 1, direction = Direction.LEFT)

        val board = BoardState(rows = 3, cols = 3, arrows = listOf(arrowBlocked, arrowBlocker))
        val available = board.getAvailableMoves()

        assertFalse("La freccia bloccata non deve essere tra le mosse disponibili", available.any { it.id == 1 })
        assertTrue("La freccia libera verso sinistra deve essere disponibile", available.any { it.id == 2 })
    }

    @Test
    fun removingArrowShouldUnlockNextArrow() {
        val arrowBlocked = Arrow(id = 1, row = 1, col = 1, direction = Direction.UP)
        val arrowBlocker = Arrow(id = 2, row = 0, col = 1, direction = Direction.LEFT)

        val board = BoardState(rows = 3, cols = 3, arrows = listOf(arrowBlocked, arrowBlocker))
        val stateAfterMove = board.applyMove(arrowBlocker)

        val availableAfter = stateAfterMove.getAvailableMoves()
        assertTrue("Rimuovere il bloccante deve sbloccare la freccia 1", availableAfter.any { it.id == 1 })
    }

    @Test
    fun solverShouldDetectDeadlockCycle() = runBlocking {
        // Ciclo a 4 frecce bloccate a vicenda:
        // (0,0) punta a DESTRA verso (0,1)
        // (0,1) punta in BASSO verso (1,1)
        // (1,1) punta a SINISTRA verso (1,0)
        // (1,0) punta in ALTO verso (0,0)
        val a1 = Arrow(id = 1, row = 0, col = 0, direction = Direction.RIGHT)
        val a2 = Arrow(id = 2, row = 0, col = 1, direction = Direction.DOWN)
        val a3 = Arrow(id = 3, row = 1, col = 1, direction = Direction.LEFT)
        val a4 = Arrow(id = 4, row = 1, col = 0, direction = Direction.UP)

        val cycleBoard = BoardState(rows = 2, cols = 2, arrows = listOf(a1, a2, a3, a4))
        val result = solver.solve(cycleBoard)

        assertFalse("Un ciclo deadlock non deve essere dichiarato risolto", result.isSolved)
    }

    @Test
    fun solverBenchmarkOnRandomSolvableBoards() = runBlocking {
        for (i in 1..25) {
            val randomBoard = solver.generateSolvableBoard(rows = 5, cols = 5, arrowCount = 8)
            val result = solver.solve(randomBoard)
            // La generazione può produrre sia configurazioni risolvibili che complesse
            assertTrue("Il tempo di calcolo deve essere < 200ms per griglie 5x5", result.calculationTimeMs < 200)
        }
    }
}
