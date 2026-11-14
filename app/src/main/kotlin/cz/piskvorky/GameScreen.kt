package cz.piskvorky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.piskvorky.game.GameState
import cz.piskvorky.game.Player

private val ColorX = Color(0xFF1E66D0)
private val ColorO = Color(0xFFD63A3A)
private val ColorWin = Color(0xFFFFB300)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: GameViewModel = viewModel()) {
    val state = vm.state
    val online = vm.mode == GameMode.ONLINE
    val settingsEnabled = !vm.settingsLocked
    val showGame = !online || vm.onlineStatus == OnlineStatus.PLAYING

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = vm.mode == GameMode.VS_COMPUTER,
                    onClick = { vm.selectMode(GameMode.VS_COMPUTER) },
                    label = { Text(stringResource(R.string.mode_vs_computer)) },
                )
                FilterChip(
                    selected = vm.mode == GameMode.TWO_PLAYERS,
                    onClick = { vm.selectMode(GameMode.TWO_PLAYERS) },
                    label = { Text(stringResource(R.string.mode_two_players)) },
                )
                FilterChip(
                    selected = online,
                    onClick = { vm.selectMode(GameMode.ONLINE) },
                    label = { Text(stringResource(R.string.mode_online)) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (size in GameViewModel.BOARD_SIZES) {
                    FilterChip(
                        selected = state.size == size,
                        onClick = { vm.selectSize(size) },
                        enabled = settingsEnabled,
                        label = { Text(stringResource(R.string.board_size, size)) },
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        when {
                            online && vm.settingsLocked -> R.string.symbol_starts
                            vm.mode == GameMode.TWO_PLAYERS -> R.string.symbol_starts
                            else -> R.string.symbol_yours
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilterChip(
                    selected = state.first == Player.X,
                    onClick = { vm.selectSymbol(Player.X) },
                    enabled = settingsEnabled,
                    label = { Text(stringResource(R.string.symbol_x), color = ColorX) },
                )
                FilterChip(
                    selected = state.first == Player.O,
                    onClick = { vm.selectSymbol(Player.O) },
                    enabled = settingsEnabled,
                    label = { Text(stringResource(R.string.symbol_o), color = ColorO) },
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.target_wins),
                    style = MaterialTheme.typography.bodyMedium,
                )
                for (wins in GameViewModel.TARGET_WINS) {
                    FilterChip(
                        selected = vm.targetWins == wins,
                        onClick = { vm.selectTargetWins(wins) },
                        enabled = settingsEnabled,
                        label = { Text(wins.toString()) },
                    )
                }
            }

            if (online) OnlinePanel(vm)

            if (showGame) {
                ScoreRow(vm.mode, vm.localPlayer, vm.scoreX, vm.scoreO)

                Text(
                    text = statusText(
                        state = state,
                        mode = vm.mode,
                        localPlayer = vm.localPlayer,
                        isThinking = vm.isThinking,
                        matchWinner = vm.matchWinner.takeIf { vm.targetWins > 1 },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = when {
                        state.winner == Player.X -> ColorX
                        state.winner == Player.O -> ColorO
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )

                BoardView(
                    state = state,
                    onCellTap = vm::onCellTap,
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!online) {
                        OutlinedButton(onClick = vm::undo, enabled = state.moves.isNotEmpty()) {
                            Text(stringResource(R.string.undo))
                        }
                    }
                    Button(onClick = vm::newGame) {
                        Text(
                            stringResource(
                                when {
                                    vm.matchWinner != null && vm.targetWins > 1 -> R.string.new_match
                                    state.isOver -> R.string.next_game
                                    else -> R.string.new_game
                                },
                            ),
                        )
                    }
                }

                Text(
                    text = stringResource(if (state.winLength == 3) R.string.rules_three else R.string.rules_five),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Ovládání síťové hry: založení místnosti, připojení kódem a stav spojení. */
@Composable
private fun OnlinePanel(vm: GameViewModel) {
    var codeInput by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (vm.onlineStatus) {
            OnlineStatus.OFF -> {
                Text(
                    text = stringResource(R.string.online_intro),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = vm::hostGame) { Text(stringResource(R.string.online_host)) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { text ->
                            codeInput = text.uppercase()
                                .filter { it in GameViewModel.CODE_ALPHABET }
                                .take(GameViewModel.ROOM_CODE_LENGTH)
                        },
                        label = { Text(stringResource(R.string.online_code_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = { vm.joinGame(codeInput) },
                        enabled = codeInput.length == GameViewModel.ROOM_CODE_LENGTH,
                    ) {
                        Text(stringResource(R.string.online_join))
                    }
                }
            }
            OnlineStatus.CONNECTING -> {
                Text(stringResource(R.string.online_connecting))
                TextButton(onClick = vm::leaveOnline) { Text(stringResource(R.string.online_cancel)) }
            }
            OnlineStatus.WAITING_FOR_OPPONENT -> {
                Text(stringResource(R.string.online_code_title), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = vm.roomCode,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                )
                Text(
                    text = stringResource(R.string.online_waiting_opponent),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = vm::leaveOnline) { Text(stringResource(R.string.online_cancel)) }
            }
            OnlineStatus.WAITING_FOR_HOST -> {
                Text(
                    text = stringResource(R.string.online_waiting_host, vm.roomCode),
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = vm::leaveOnline) { Text(stringResource(R.string.online_cancel)) }
            }
            OnlineStatus.PLAYING -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.online_playing, vm.roomCode),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = vm::leaveOnline) { Text(stringResource(R.string.online_leave)) }
            }
            OnlineStatus.OPPONENT_LEFT -> {
                Text(stringResource(R.string.online_opponent_left))
                Button(onClick = vm::leaveOnline) { Text(stringResource(R.string.online_close)) }
            }
            OnlineStatus.ERROR -> {
                Text(
                    text = stringResource(R.string.online_error),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = vm::leaveOnline) { Text(stringResource(R.string.online_close)) }
            }
        }
    }
}

@Composable
private fun statusText(
    state: GameState,
    mode: GameMode,
    localPlayer: Player?,
    isThinking: Boolean,
    matchWinner: Player?,
): String {
    val online = mode == GameMode.ONLINE
    val winner = state.winner
    return when {
        matchWinner != null && localPlayer != null -> stringResource(
            when {
                matchWinner == localPlayer -> R.string.status_you_win_match
                online -> R.string.status_opponent_win_match
                else -> R.string.status_computer_win_match
            },
        )
        matchWinner != null -> stringResource(R.string.status_win_match, matchWinner.name)
        winner != null && localPlayer != null -> stringResource(
            when {
                winner == localPlayer -> R.string.status_you_win
                online -> R.string.status_opponent_win
                else -> R.string.status_computer_win
            },
        )
        winner != null -> stringResource(R.string.status_win, winner.name)
        state.isDraw -> stringResource(R.string.status_draw)
        isThinking -> stringResource(R.string.status_thinking)
        localPlayer != null && state.current == localPlayer ->
            stringResource(R.string.status_your_turn, localPlayer.name)
        localPlayer != null -> stringResource(R.string.status_opponent_turn)
        else -> stringResource(R.string.status_turn, state.current.name)
    }
}

@Composable
private fun scoreLabel(mode: GameMode, localPlayer: Player?, player: Player): String = when {
    localPlayer == null -> stringResource(R.string.score_player, player.name)
    player == localPlayer -> stringResource(R.string.score_you, player.name)
    mode == GameMode.ONLINE -> stringResource(R.string.score_opponent, player.name)
    else -> stringResource(R.string.score_computer, player.name)
}

@Composable
private fun ScoreRow(mode: GameMode, localPlayer: Player?, scoreX: Int, scoreO: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = scoreLabel(mode, localPlayer, Player.X),
            color = ColorX,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "$scoreX : $scoreO",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = scoreLabel(mode, localPlayer, Player.O),
            color = ColorO,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BoardView(
    state: GameState,
    onCellTap: (row: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val n = state.size
    val currentOnCellTap by rememberUpdatedState(onCellTap)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val boardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val lastMoveColor = MaterialTheme.colorScheme.secondaryContainer

    Canvas(
        modifier = modifier
            .background(boardColor)
            .pointerInput(n) {
                detectTapGestures { offset ->
                    val cell = size.width.toFloat() / n
                    val row = (offset.y / cell).toInt().coerceIn(0, n - 1)
                    val col = (offset.x / cell).toInt().coerceIn(0, n - 1)
                    currentOnCellTap(row, col)
                }
            },
    ) {
        val cell = size.width / n
        val cellSize = Size(cell, cell)

        state.lastMove?.let { move ->
            drawRect(lastMoveColor, topLeft = Offset(move.col * cell, move.row * cell), size = cellSize)
        }
        for (winCell in state.winningLine) {
            drawRect(
                color = ColorWin.copy(alpha = 0.35f),
                topLeft = Offset(winCell.col * cell, winCell.row * cell),
                size = cellSize,
            )
        }

        val gridStroke = 1.dp.toPx()
        for (i in 0..n) {
            val p = i * cell
            drawLine(gridColor, Offset(p, 0f), Offset(p, size.height), gridStroke)
            drawLine(gridColor, Offset(0f, p), Offset(size.width, p), gridStroke)
        }

        val markStroke = cell * 0.13f
        val inset = cell * 0.24f
        for (row in 0 until n) {
            for (col in 0 until n) {
                val left = col * cell
                val top = row * cell
                when (state[row, col]) {
                    Player.X -> {
                        drawLine(
                            ColorX,
                            Offset(left + inset, top + inset),
                            Offset(left + cell - inset, top + cell - inset),
                            markStroke,
                            StrokeCap.Round,
                        )
                        drawLine(
                            ColorX,
                            Offset(left + cell - inset, top + inset),
                            Offset(left + inset, top + cell - inset),
                            markStroke,
                            StrokeCap.Round,
                        )
                    }
                    Player.O -> drawCircle(
                        color = ColorO,
                        radius = cell / 2 - inset,
                        center = Offset(left + cell / 2, top + cell / 2),
                        style = Stroke(width = markStroke),
                    )
                    null -> Unit
                }
            }
        }

        if (state.winningLine.size >= 2) {
            val first = state.winningLine.first()
            val last = state.winningLine.last()
            drawLine(
                color = ColorWin,
                start = Offset((first.col + 0.5f) * cell, (first.row + 0.5f) * cell),
                end = Offset((last.col + 0.5f) * cell, (last.row + 0.5f) * cell),
                strokeWidth = cell * 0.12f,
                cap = StrokeCap.Round,
            )
        }
    }
}
