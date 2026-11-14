package cz.piskvorky.game

import kotlin.math.abs
import kotlin.random.Random

/**
 * Počítačový protihráč. Malou desku (3×3) propočítá celou do konce, takže
 * na ní neprohraje. Na větších deskách každé volné pole v okolí kamenů ohodnotí podle toho,
 * jak silnou řadu by na něm vytvořil sám (útok) a jakou by tím zkazil
 * soupeři (obrana), a vybere pole s nejvyšším součtem.
 */
object Ai {
    private const val DEFENSE_WEIGHT = 0.8
    private const val NEIGHBOR_DISTANCE = 2
    private const val FULL_SEARCH_MAX_CELLS = 9
    private const val WIN_SCORE = 1000

    /** Tabulka v [patternScore] je sestavená pro hru na pět v řadě. */
    private const val STANDARD_WIN_LENGTH = 5

    fun chooseMove(state: GameState, random: Random = Random.Default): Cell {
        check(!state.isOver) { "Partie už skončila" }
        val size = state.size
        if (state.moves.isEmpty()) return Cell(size / 2, size / 2)
        if (size * size <= FULL_SEARCH_MAX_CELLS) return fullSearch(state, random)

        val me = state.current
        var bestScore = -1.0
        val best = mutableListOf<Cell>()
        for (row in 0 until size) {
            for (col in 0 until size) {
                if (state[row, col] != null || !hasNeighbor(state, row, col)) continue
                val score = evaluate(state, row, col, me) +
                    DEFENSE_WEIGHT * evaluate(state, row, col, me.other)
                if (score > bestScore) {
                    bestScore = score
                    best.clear()
                }
                if (score == bestScore) best += Cell(row, col)
            }
        }
        return best.random(random)
    }

    /** Úplné prohledání hry (minimax); mezi stejně dobrými tahy vybírá náhodně. */
    private fun fullSearch(state: GameState, random: Random): Cell {
        var bestScore = Int.MIN_VALUE
        val best = mutableListOf<Cell>()
        for (cell in emptyCells(state)) {
            val score = -negamax(state.play(cell.row, cell.col), 1)
            if (score > bestScore) {
                bestScore = score
                best.clear()
            }
            if (score == bestScore) best += cell
        }
        return best.random(random)
    }

    /** Hodnota pozice z pohledu hráče na tahu; dřívější výhra má vyšší cenu. */
    private fun negamax(state: GameState, depth: Int): Int {
        if (state.winner != null) return depth - WIN_SCORE
        if (state.isDraw) return 0
        var best = Int.MIN_VALUE
        for (cell in emptyCells(state)) {
            val score = -negamax(state.play(cell.row, cell.col), depth + 1)
            if (score > best) best = score
        }
        return best
    }

    private fun emptyCells(state: GameState): List<Cell> = buildList {
        for (row in 0 until state.size) {
            for (col in 0 until state.size) {
                if (state[row, col] == null) add(Cell(row, col))
            }
        }
    }

    private fun hasNeighbor(state: GameState, row: Int, col: Int): Boolean {
        for (dr in -NEIGHBOR_DISTANCE..NEIGHBOR_DISTANCE) {
            for (dc in -NEIGHBOR_DISTANCE..NEIGHBOR_DISTANCE) {
                val r = row + dr
                val c = col + dc
                if (state.inBounds(r, c) && state[r, c] != null) return true
            }
        }
        return false
    }

    /** Hodnota pole pro hráče [player], kdyby na něj položil svou značku. */
    private fun evaluate(state: GameState, row: Int, col: Int, player: Player): Double =
        GameState.DIRECTIONS.sumOf { (dr, dc) ->
            val forward = scan(state, row, col, dr, dc, player)
            val backward = scan(state, row, col, -dr, -dc, player)
            val count = forward.stones + backward.stones
            val room = 1 + count + forward.free + backward.free
            if (room < state.winLength) {
                0.0
            } else {
                val openEnds = (if (forward.free > 0) 1 else 0) + (if (backward.free > 0) 1 else 0)
                patternScore(count + STANDARD_WIN_LENGTH - state.winLength, openEnds)
            }
        }

    private class Run(val stones: Int, val free: Int)

    /** Spočítá souvislé vlastní kameny jedním směrem a volná pole hned za nimi. */
    private fun scan(state: GameState, row: Int, col: Int, dr: Int, dc: Int, player: Player): Run {
        var r = row + dr
        var c = col + dc
        var stones = 0
        while (state.inBounds(r, c) && state[r, c] == player) {
            stones++
            r += dr
            c += dc
        }
        var free = 0
        while (free < state.winLength && state.inBounds(r, c) && state[r, c] == null) {
            free++
            r += dr
            c += dc
        }
        return Run(stones, free)
    }

    /** [count] = počet sousedních vlastních kamenů v řadě, [openEnds] = volné konce (0–2). */
    private fun patternScore(count: Int, openEnds: Int): Double = when {
        count >= 4 -> 1_000_000.0
        openEnds == 0 -> 0.0
        count == 3 -> if (openEnds == 2) 100_000.0 else 1_500.0
        count == 2 -> if (openEnds == 2) 1_200.0 else 150.0
        count == 1 -> if (openEnds == 2) 100.0 else 10.0
        else -> if (openEnds == 2) 4.0 else 1.0
    }
}
