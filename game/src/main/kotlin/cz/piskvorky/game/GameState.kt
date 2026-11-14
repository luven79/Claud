package cz.piskvorky.game

enum class Player {
    X, O;

    val other: Player get() = if (this == X) O else X
}

data class Cell(val row: Int, val col: Int)

/**
 * Neměnný stav partie piškvorek. Začíná vždy hráč X, vyhrává řada
 * alespoň [winLength] stejných značek vodorovně, svisle nebo šikmo.
 */
data class GameState(
    val size: Int,
    val winLength: Int,
    val cells: List<Player?>,
    val moves: List<Cell>,
    val winner: Player?,
    val winningLine: List<Cell>,
) {
    val current: Player get() = if (moves.size % 2 == 0) Player.X else Player.O
    val isDraw: Boolean get() = winner == null && moves.size == size * size
    val isOver: Boolean get() = winner != null || isDraw
    val lastMove: Cell? get() = moves.lastOrNull()

    operator fun get(row: Int, col: Int): Player? = cells[row * size + col]

    fun inBounds(row: Int, col: Int): Boolean = row in 0 until size && col in 0 until size

    fun canPlay(row: Int, col: Int): Boolean = !isOver && inBounds(row, col) && this[row, col] == null

    /** Zahraje tah hráče, který je na řadě. Neplatný tah vrací nezměněný stav. */
    fun play(row: Int, col: Int): GameState {
        if (!canPlay(row, col)) return this
        val player = current
        val newCells = cells.toMutableList()
        newCells[row * size + col] = player
        val line = findWinningLine(newCells, size, winLength, row, col, player)
        return copy(
            cells = newCells,
            moves = moves + Cell(row, col),
            winner = if (line.isEmpty()) null else player,
            winningLine = line,
        )
    }

    /** Vrátí posledních [count] tahů. */
    fun undo(count: Int = 1): GameState =
        moves.dropLast(count).fold(new(size, winLength)) { state, move -> state.play(move.row, move.col) }

    companion object {
        /** Na desce 3×3 se hraje na tři v řadě, jinak na pět. */
        fun defaultWinLength(size: Int): Int = if (size <= 3) 3 else 5

        internal val DIRECTIONS = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)

        fun new(size: Int = 15, winLength: Int = defaultWinLength(size)): GameState {
            require(size > 0) { "Velikost desky musí být kladná" }
            require(winLength > 1) { "Délka vítězné řady musí být alespoň 2" }
            return GameState(size, winLength, List(size * size) { null }, emptyList(), null, emptyList())
        }

        private fun findWinningLine(
            cells: List<Player?>,
            size: Int,
            winLength: Int,
            row: Int,
            col: Int,
            player: Player,
        ): List<Cell> {
            fun owned(r: Int, c: Int) =
                r in 0 until size && c in 0 until size && cells[r * size + c] == player

            for ((dr, dc) in DIRECTIONS) {
                var startRow = row
                var startCol = col
                while (owned(startRow - dr, startCol - dc)) {
                    startRow -= dr
                    startCol -= dc
                }
                val line = mutableListOf<Cell>()
                var r = startRow
                var c = startCol
                while (owned(r, c)) {
                    line += Cell(r, c)
                    r += dr
                    c += dc
                }
                if (line.size >= winLength) return line
            }
            return emptyList()
        }
    }
}
