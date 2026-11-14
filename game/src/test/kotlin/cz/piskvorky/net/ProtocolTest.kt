package cz.piskvorky.net

import cz.piskvorky.game.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProtocolTest {
    @Test
    fun `every message survives a round trip`() {
        val messages = listOf(
            NetMessage.Join,
            NetMessage.Start(15, 5, Player.O, 3),
            NetMessage.Start(3, 3, Player.X, 1),
            NetMessage.Move(0, 7, 7),
            NetMessage.Move(224, 14, 0),
            NetMessage.NewGame,
            NetMessage.Leave,
        )
        for (message in messages) {
            assertEquals("abc123" to message, Protocol.decode(Protocol.encode("abc123", message)))
        }
    }

    @Test
    fun `garbage is rejected`() {
        val bad = listOf(
            "",
            "hello",
            "pk1;;J",
            "pk2;a;J",
            "pk1;a;X",
            "pk1;a;J;1",
            "pk1;a;M;1;2",
            "pk1;a;M;1;x;3",
            "pk1;a;M;-1;2;3",
            "pk1;a;S;15;5;Z;1",
            "pk1;a;S;999;5;X;1",
            "pk1;a;S;3;5;X;1",
            "pk1;a;S;15;5;X;0",
        )
        for (text in bad) assertNull(Protocol.decode(text), "mělo být odmítnuto: '$text'")
    }
}
