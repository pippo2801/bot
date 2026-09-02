package com.amazego.autosolver

import com.amazego.autosolver.model.Arrow
import com.amazego.autosolver.model.BoardState
import com.amazego.autosolver.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardStateTest {

    @Test
    fun testBoardStateCompleteness() {
        val a1 = Arrow(id = 1, row = 0, col = 0, direction = Direction.UP)
        val a2 = Arrow(id = 2, row = 0, col = 1, direction = Direction.UP)

        var state = BoardState(rows = 1, cols = 2, arrows = listOf(a1, a2))
        assertEquals(2, state.getAvailableMoves().size)

        state = state.applyMove(a1)
        assertEquals(1, state.removedArrowIds.size)

        state = state.applyMove(a2)
        assertTrue("Lo stato finale deve essere completo", state.isComplete())
    }
}
