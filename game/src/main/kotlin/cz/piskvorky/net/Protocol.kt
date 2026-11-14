package cz.piskvorky.net

import cz.piskvorky.game.Player

/** Zprávy, které si posílají dva telefony při hře po síti. */
sealed interface NetMessage {
    /** Host se připojil do místnosti. */
    data object Join : NetMessage

    /** Odpověď zakladatele: nastavení zápasu. Zakladatel hraje za [hostSymbol] a začíná. */
    data class Start(
        val size: Int,
        val winLength: Int,
        val hostSymbol: Player,
        val targetWins: Int,
    ) : NetMessage

    /** Tah; [index] je pořadí tahu v partii (od nuly), aby se dal poznat duplikát. */
    data class Move(val index: Int, val row: Int, val col: Int) : NetMessage

    /** Další hra (nebo nový zápas po jeho skončení). */
    data object NewGame : NetMessage

    /** Hráč odešel. */
    data object Leave : NetMessage
}

/** Textový formát zpráv: `pk1;odesílatel;typ;parametry…`. */
object Protocol {
    private const val PREFIX = "pk1"
    private const val MAX_SIZE = 30

    fun encode(sender: String, message: NetMessage): String {
        val body = when (message) {
            NetMessage.Join -> "J"
            is NetMessage.Start ->
                "S;${message.size};${message.winLength};${message.hostSymbol.name};${message.targetWins}"
            is NetMessage.Move -> "M;${message.index};${message.row};${message.col}"
            NetMessage.NewGame -> "N"
            NetMessage.Leave -> "L"
        }
        return "$PREFIX;$sender;$body"
    }

    /** Vrátí odesílatele a zprávu, nebo null, pokud text není platná zpráva. */
    fun decode(text: String): Pair<String, NetMessage>? {
        val parts = text.trim().split(';')
        if (parts.size < 3 || parts[0] != PREFIX || parts[1].isEmpty()) return null
        val args = parts.drop(3)
        val message: NetMessage? = when (parts[2]) {
            "J" -> NetMessage.Join.takeIf { args.isEmpty() }
            "N" -> NetMessage.NewGame.takeIf { args.isEmpty() }
            "L" -> NetMessage.Leave.takeIf { args.isEmpty() }
            "S" -> decodeStart(args)
            "M" -> decodeMove(args)
            else -> null
        }
        return message?.let { parts[1] to it }
    }

    private fun decodeStart(args: List<String>): NetMessage.Start? {
        if (args.size != 4) return null
        val size = args[0].toIntOrNull() ?: return null
        val winLength = args[1].toIntOrNull() ?: return null
        val symbol = Player.entries.firstOrNull { it.name == args[2] } ?: return null
        val targetWins = args[3].toIntOrNull() ?: return null
        if (size !in 3..MAX_SIZE || winLength !in 2..size || targetWins !in 1..99) return null
        return NetMessage.Start(size, winLength, symbol, targetWins)
    }

    private fun decodeMove(args: List<String>): NetMessage.Move? {
        if (args.size != 3) return null
        val numbers = args.map { it.toIntOrNull() ?: return null }
        if (numbers.any { it < 0 }) return null
        return NetMessage.Move(numbers[0], numbers[1], numbers[2])
    }
}
