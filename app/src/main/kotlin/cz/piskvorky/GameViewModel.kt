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
import cz.piskvorky.net.NetMessage
import cz.piskvorky.net.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class GameMode { VS_COMPUTER, TWO_PLAYERS, ONLINE }

enum class OnlineStatus {
    /** Žádná síťová hra neběží. */
    OFF,

    /** Navazuje se spojení se serverem. */
    CONNECTING,

    /** Zakladatel čeká, až se soupeř připojí. */
    WAITING_FOR_OPPONENT,

    /** Host čeká na odpověď zakladatele. */
    WAITING_FOR_HOST,
    PLAYING,
    OPPONENT_LEFT,
    ERROR,
}

/**
 * Drží stav partie přes otočení displeje. Začíná vždy značka [GameState.first]:
 * proti počítači je to značka člověka, po síti značka zakladatele hry.
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

    /** Na kolik vyhraných her se hraje zápas. */
    var targetWins by mutableIntStateOf(1)
        private set

    var onlineStatus by mutableStateOf(OnlineStatus.OFF)
        private set

    /** Kód místnosti, který si hráči sdělí. */
    var roomCode by mutableStateOf("")
        private set

    /** Hráč, který už dosáhl [targetWins] výher, jinak null. */
    val matchWinner: Player?
        get() = when {
            scoreX >= targetWins -> Player.X
            scoreO >= targetWins -> Player.O
            else -> null
        }

    /** Značka hráče u tohoto telefonu; null, když se o telefon dělí dva hráči. */
    val localPlayer: Player?
        get() = when (mode) {
            GameMode.VS_COMPUTER -> state.first
            GameMode.TWO_PLAYERS -> null
            GameMode.ONLINE -> if (isHost) state.first else state.first.other
        }

    /** Nastavení hry jde měnit jen mimo rozehranou síťovou hru. */
    val settingsLocked: Boolean
        get() = mode == GameMode.ONLINE && onlineStatus != OnlineStatus.OFF

    private var aiJob: Job? = null

    private class Session(val channel: NtfyChannel, val outbox: Channel<String>, val job: Job)

    private var session: Session? = null
    private var isHost by mutableStateOf(true)
    private val clientId = randomString(8)

    fun onCellTap(row: Int, col: Int) {
        if (isThinking || !state.canPlay(row, col)) return
        if (mode == GameMode.ONLINE) {
            if (onlineStatus != OnlineStatus.PLAYING || state.current != localPlayer) return
            send(NetMessage.Move(state.moves.size, row, col))
            applyMove(row, col)
            return
        }
        applyMove(row, col)
        if (mode == GameMode.VS_COMPUTER && !state.isOver) computerMove()
    }

    /** Začne další hru; po skončení zápasu zároveň vynuluje skóre. */
    fun newGame() {
        if (mode == GameMode.ONLINE) {
            if (onlineStatus != OnlineStatus.PLAYING) return
            send(NetMessage.NewGame)
        }
        startNextGame()
    }

    fun undo() {
        if (mode == GameMode.ONLINE || state.moves.isEmpty()) return
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
        leaveOnline()
        mode = newMode
        resetAll(state.size, state.first)
    }

    /** Zvolí značku, kterou se začíná (proti počítači značku člověka). */
    fun selectSymbol(symbol: Player) {
        if (settingsLocked || symbol == state.first) return
        resetAll(state.size, symbol)
    }

    fun selectSize(size: Int) {
        if (settingsLocked || size == state.size) return
        resetAll(size, state.first)
    }

    fun selectTargetWins(wins: Int) {
        if (settingsLocked || wins == targetWins) return
        targetWins = wins
        resetAll(state.size, state.first)
    }

    // ---- Hra po síti ----

    /** Založí místnost s aktuálním nastavením a čeká na soupeře. */
    fun hostGame() {
        if (mode != GameMode.ONLINE) return
        leaveOnline()
        isHost = true
        resetAll(state.size, state.first)
        openSession(randomString(ROOM_CODE_LENGTH)) {
            onlineStatus = OnlineStatus.WAITING_FOR_OPPONENT
        }
    }

    /** Připojí se do místnosti, kterou založil soupeř. */
    fun joinGame(code: String) {
        val cleaned = code.trim().uppercase()
        if (mode != GameMode.ONLINE || cleaned.length != ROOM_CODE_LENGTH || cleaned.any { it !in CODE_ALPHABET }) return
        leaveOnline()
        isHost = false
        openSession(cleaned) {
            onlineStatus = OnlineStatus.WAITING_FOR_HOST
            send(NetMessage.Join)
        }
    }

    /** Ukončí síťovou hru a dá o tom vědět soupeři. */
    fun leaveOnline() {
        val current = session ?: run {
            onlineStatus = OnlineStatus.OFF
            return
        }
        val wasConnected = onlineStatus == OnlineStatus.PLAYING ||
            onlineStatus == OnlineStatus.WAITING_FOR_OPPONENT ||
            onlineStatus == OnlineStatus.WAITING_FOR_HOST
        session = null
        current.job.cancel()
        current.channel.close()
        if (wasConnected) {
            val goodbye = Protocol.encode(clientId, NetMessage.Leave)
            // Mimo viewModelScope, aby se rozloučení odeslalo i při zavírání aplikace.
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    current.channel.publish(goodbye)
                } catch (e: IOException) {
                    // Soupeř se o odchodu nedozví; nic dalšího se s tím dělat nedá.
                }
            }
        }
        onlineStatus = OnlineStatus.OFF
        roomCode = ""
        isHost = true
    }

    private fun openSession(code: String, onOpen: () -> Unit) {
        roomCode = code
        onlineStatus = OnlineStatus.CONNECTING
        val channel = NtfyChannel(TOPIC_PREFIX + code.lowercase())
        val outbox = Channel<String>(Channel.UNLIMITED)
        val job = viewModelScope.launch {
            launch {
                for (text in outbox) {
                    try {
                        channel.publish(text)
                    } catch (e: IOException) {
                        onlineStatus = OnlineStatus.ERROR
                    }
                }
            }
            channel.listen(
                onOpen = { withContext(Dispatchers.Main) { onOpen() } },
                onMessage = { text -> withContext(Dispatchers.Main) { onNetText(text) } },
            )
        }
        session = Session(channel, outbox, job)
    }

    private fun send(message: NetMessage) {
        session?.outbox?.trySend(Protocol.encode(clientId, message))
    }

    private fun onNetText(text: String) {
        val (sender, message) = Protocol.decode(text) ?: return
        if (sender == clientId || session == null) return
        when (message) {
            NetMessage.Join -> if (isHost) {
                resetAll(state.size, state.first)
                onlineStatus = OnlineStatus.PLAYING
                send(NetMessage.Start(state.size, state.winLength, state.first, targetWins))
            }
            is NetMessage.Start -> if (!isHost) {
                targetWins = message.targetWins
                scoreX = 0
                scoreO = 0
                state = GameState.new(message.size, message.winLength, message.hostSymbol)
                onlineStatus = OnlineStatus.PLAYING
            }
            is NetMessage.Move -> {
                val opponentsTurn = state.current != localPlayer
                if (onlineStatus == OnlineStatus.PLAYING && opponentsTurn &&
                    message.index == state.moves.size && state.canPlay(message.row, message.col)
                ) {
                    applyMove(message.row, message.col)
                }
            }
            NetMessage.NewGame -> if (onlineStatus == OnlineStatus.PLAYING) startNextGame()
            NetMessage.Leave -> if (onlineStatus != OnlineStatus.OFF) {
                onlineStatus = OnlineStatus.OPPONENT_LEFT
            }
        }
    }

    // ---- Společné ----

    private fun startNextGame() {
        cancelAi()
        if (matchWinner != null) {
            scoreX = 0
            scoreO = 0
        }
        state = GameState.new(state.size, state.winLength, state.first)
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

    override fun onCleared() {
        leaveOnline()
        super.onCleared()
    }

    companion object {
        const val DEFAULT_SIZE = 15
        const val ROOM_CODE_LENGTH = 5
        val BOARD_SIZES = listOf(3, 10, 15)
        val TARGET_WINS = listOf(1, 2, 3)

        /** Bez znaků, které se snadno pletou (0/O, 1/I). */
        const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        private const val TOPIC_PREFIX = "piskvorky-hra-"
        private const val THINKING_DELAY_MS = 350L

        private fun randomString(length: Int): String =
            (1..length).map { CODE_ALPHABET.random() }.joinToString("")
    }
}
