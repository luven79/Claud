package cz.piskvorky.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiTest {
    private fun play(vararg moves: Pair<Int, Int>): GameState =
        moves.fold(GameState.new(15)) { s, (r, c) -> s.play(r, c) }

    @Test
    fun `opens in the center`() {
        assertEquals(Cell(7, 7), Ai.chooseMove(GameState.new(15)))
        assertEquals(Cell(5, 5), Ai.chooseMove(GameState.new(10)))
    }

    @Test
    fun `completes its own five`() {
        // O má čtyři v řadě s jedním volným koncem, X má otevřenou trojici.
        val s = play(7 to 3, 2 to 2, 7 to 4, 2 to 3, 7 to 5, 2 to 4, 2 to 1, 2 to 5, 12 to 12)
        assertEquals(Player.O, s.current)
        val after = Ai.chooseMove(s).let { s.play(it.row, it.col) }
        assertEquals(Player.O, after.winner)
    }

    @Test
    fun `prefers winning over blocking`() {
        // Oba mají čtyřku, na tahu je O – má vyhrát, ne bránit.
        val s = play(7 to 3, 2 to 2, 7 to 4, 2 to 3, 7 to 5, 2 to 4, 7 to 6, 2 to 5, 12 to 12)
        val after = Ai.chooseMove(s).let { s.play(it.row, it.col) }
        assertEquals(Player.O, after.winner)
    }

    @Test
    fun `blocks opponent four`() {
        val s = play(7 to 3, 0 to 0, 7 to 4, 0 to 14, 7 to 5, 14 to 0, 7 to 6)
        assertEquals(Player.O, s.current)
        assertContains(listOf(Cell(7, 2), Cell(7, 7)), Ai.chooseMove(s))
    }

    @Test
    fun `blocks the gap in a broken four`() {
        val s = play(7 to 3, 0 to 0, 7 to 4, 0 to 14, 7 to 6, 14 to 0, 7 to 7)
        assertEquals(Cell(7, 5), Ai.chooseMove(s))
    }

    @Test
    fun `blocks an open three`() {
        val s = play(7 to 5, 0 to 0, 7 to 6, 14 to 14, 7 to 7)
        assertContains(listOf(Cell(7, 4), Cell(7, 8)), Ai.chooseMove(s))
    }

    @Test
    fun `on three by three it wins when it can and blocks otherwise`() {
        // X: (0,0) (0,1), O: (1,1) (2,2); na tahu X – vyhraje na (0,2).
        val win = listOf(0 to 0, 1 to 1, 0 to 1, 2 to 2).fold(GameState.new(3)) { s, (r, c) -> s.play(r, c) }
        assertEquals(Cell(0, 2), Ai.chooseMove(win))

        // X: (0,0) (0,1), O: (1,1); na tahu O – musí bránit na (0,2).
        val block = listOf(0 to 0, 1 to 1, 0 to 1).fold(GameState.new(3)) { s, (r, c) -> s.play(r, c) }
        assertEquals(Cell(0, 2), Ai.chooseMove(block))
    }

    @Test
    fun `never loses on three by three against random play`() {
        repeat(200) { seed ->
            val random = Random(seed)
            val aiPlayer = if (seed % 2 == 0) Player.O else Player.X
            var s = GameState.new(3)
            while (!s.isOver) {
                val move = if (s.current == aiPlayer) {
                    Ai.chooseMove(s, random)
                } else {
                    (0 until 9).map { Cell(it / 3, it % 3) }.filter { s[it.row, it.col] == null }.random(random)
                }
                s = s.play(move.row, move.col)
            }
            assertTrue(s.winner == null || s.winner == aiPlayer, "počítač prohrál (seed $seed)")
        }
    }

    @Test
    fun `self play always produces legal moves and finishes`() {
        repeat(20) { seed ->
            val random = Random(seed)
            var s = GameState.new(if (seed % 2 == 0) 15 else 10)
            while (!s.isOver) {
                val move = Ai.chooseMove(s, random)
                assertTrue(s.canPlay(move.row, move.col), "neplatný tah $move")
                s = s.play(move.row, move.col)
            }
            assertTrue(s.winner != null || s.isDraw)
        }
    }
}
