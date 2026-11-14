package cz.piskvorky

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.piskvorky.game.Ai
import cz.piskvorky.game.GameState
import cz.piskvorky.game.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GameMode { VS_COMPUTER, TWO_PLAYERS }

/**
 * Drží stav partie přes otočení displeje. Začíná vždy značka [GameState.first];
 * v režimu proti počítači je to značka člověka.
 */
class GameViewModel : ViewModel() {
    var state by mutableStateOf(GameState.new(DEFAULT_SIZE))
        private set
    var mode by mutableStateOf(GameMode.VS_COMPUTER)
        private set
    var scoreX by mutableIntStateOf(0)
        private set
    var scoreO by mutableIntStateOf(0)
        private set
    var isThinking by mutableStateOf(false)
        private set

    private var aiJob: Job? = null

    fun onCellTap(row: Int, col: Int) {
        if (isThinking || !state.canPlay(row, col)) return
        applyMove(row, col)
        if (mode == GameMode.VS_COMPUTER && !state.isOver) computerMove()
    }

    fun newGame() {
        cancelAi()
        state = GameState.new(state.size, state.winLength, state.first)
    }

    fun undo() {
        if (state.moves.isEmpty()) return
        cancelAi()
        when (state.winner) {
            Player.X -> scoreX = (scoreX - 1).coerceAtLeast(0)
            Player.O -> scoreO = (scoreO - 1).coerceAtLeast(0)
            null -> Unit
        }
        // Proti počítači se vrací i jeho odpověď, aby byl na tahu zase člověk.
        val computerReplied = mode == GameMode.VS_COMPUTER && state.moves.size % 2 == 0
        state = state.undo(if (computerReplied) 2 else 1)
    }

    fun selectMode(newMode: GameMode) {
        if (newMode == mode) return
        mode = newMode
        resetAll(state.size, state.first)
    }

    /** Zvolí značku, kterou se začíná (proti počítači značku člověka). */
    fun selectSymbol(symbol: Player) {
        if (symbol == state.first) return
        resetAll(state.size, symbol)
    }

    fun selectSize(size: Int) {
        if (size == state.size) return
        resetAll(size, state.first)
    }

    private fun resetAll(size: Int, first: Player) {
        cancelAi()
        scoreX = 0
        scoreO = 0
        state = GameState.new(size, first = first)
    }

    private fun applyMove(row: Int, col: Int) {
        state = state.play(row, col)
        when (state.winner) {
            Player.X -> scoreX++
            Player.O -> scoreO++
            null -> Unit
        }
    }

    private fun computerMove() {
        val snapshot = state
        isThinking = true
        aiJob = viewModelScope.launch {
            val move = withContext(Dispatchers.Default) { Ai.chooseMove(snapshot) }
            delay(THINKING_DELAY_MS)
            applyMove(move.row, move.col)
            isThinking = false
        }
    }

    private fun cancelAi() {
        aiJob?.cancel()
        aiJob = null
        isThinking = false
    }

    companion object {
        const val DEFAULT_SIZE = 15
        val BOARD_SIZES = listOf(3, 10, 15)
        private const val THINKING_DELAY_MS = 350L
    }
}
