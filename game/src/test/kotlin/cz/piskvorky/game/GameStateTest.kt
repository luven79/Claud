package cz.piskvorky.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameStateTest {
    private fun play(size: Int, vararg moves: Pair<Int, Int>): GameState =
        moves.fold(GameState.new(size)) { s, (r, c) -> s.play(r, c) }

    @Test
    fun `X starts and players alternate`() {
        var s = GameState.new()
        assertEquals(Player.X, s.current)
        s = s.play(7, 7)
        assertEquals(Player.X, s[7, 7])
        assertEquals(Player.O, s.current)
        s = s.play(7, 8)
        assertEquals(Player.O, s[7, 8])
        assertEquals(Player.X, s.current)
    }

    @Test
    fun `occupied and out of bounds cells are rejected`() {
        val s = GameState.new().play(3, 3)
        assertFalse(s.canPlay(3, 3))
        assertSame(s, s.play(3, 3))
        assertFalse(s.canPlay(-1, 0))
        assertFalse(s.canPlay(0, 15))
    }

    @Test
    fun `four in a row is not a win`() {
        val s = play(15, 0 to 0, 5 to 0, 0 to 1, 5 to 1, 0 to 2, 5 to 2, 0 to 3)
        assertNull(s.winner)
        assertFalse(s.isOver)
    }

    @Test
    fun `horizontal five wins`() {
        val s = play(15, 0 to 0, 5 to 0, 0 to 1, 5 to 1, 0 to 2, 5 to 2, 0 to 3, 5 to 3, 0 to 4)
        assertEquals(Player.X, s.winner)
        assertEquals((0..4).map { Cell(0, it) }, s.winningLine)
        assertTrue(s.isOver)
        assertSame(s, s.play(9, 9))
    }

    @Test
    fun `vertical five wins for O`() {
        val s = play(
            15,
            0 to 0, 2 to 9, 0 to 1, 3 to 9, 0 to 2, 4 to 9, 0 to 3, 5 to 9, 14 to 14, 6 to 9,
        )
        assertEquals(Player.O, s.winner)
        assertEquals((2..6).map { Cell(it, 9) }, s.winningLine)
    }

    @Test
    fun `both diagonals win`() {
        val main = play(15, 1 to 1, 0 to 9, 2 to 2, 0 to 10, 3 to 3, 0 to 11, 5 to 5, 0 to 12, 4 to 4)
        assertEquals(Player.X, main.winner)
        assertEquals((1..5).map { Cell(it, it) }, main.winningLine)

        val anti = play(15, 4 to 0, 9 to 9, 3 to 1, 9 to 10, 2 to 2, 9 to 11, 1 to 3, 9 to 12, 0 to 4)
        assertEquals(Player.X, anti.winner)
        assertEquals(5, anti.winningLine.size)
        assertTrue(anti.winningLine.all { it.row + it.col == 4 })
    }

    @Test
    fun `six in a row also wins`() {
        val s = play(
            15,
            0 to 0, 5 to 0, 0 to 1, 5 to 1, 0 to 2, 5 to 2, 0 to 4, 5 to 3, 0 to 5, 9 to 9, 0 to 3,
        )
        assertEquals(Player.X, s.winner)
        assertEquals(6, s.winningLine.size)
    }

    @Test
    fun `full board without five is a draw`() {
        var s = GameState.new(3)
        for (r in 0 until 3) for (c in 0 until 3) s = s.play(r, c)
        assertNull(s.winner)
        assertTrue(s.isDraw)
        assertTrue(s.isOver)
    }

    @Test
    fun `undo removes moves and clears the win`() {
        val won = play(15, 0 to 0, 5 to 0, 0 to 1, 5 to 1, 0 to 2, 5 to 2, 0 to 3, 5 to 3, 0 to 4)
        val back = won.undo()
        assertNull(back.winner)
        assertTrue(back.winningLine.isEmpty())
        assertNull(back[0, 4])
        assertEquals(Player.X, back.current)
        assertEquals(6, won.undo(3).moves.size)
        assertEquals(GameState.new(), won.undo(100))
    }
}
