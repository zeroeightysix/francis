package me.zeroeightsix.francis

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.zeroeightsix.francis.bot.Francis
import me.zeroeightsix.francis.communicate.BrokerConnection
import me.zeroeightsix.francis.communicate.Database
import java.nio.file.Files
import java.nio.file.Path

typealias PlayerID = String

fun main() {
    val config = Json.decodeFromString<Config>(Files.readString(Path.of("config.json")))
    Database.init(config)

    BrokerConnection(config, Francis())
}

@Serializable
data class Player(val username: String, val uuid: PlayerID) {
    /**
     * @return `true` if this player has no known associated UUID
     */
    fun isFugitive(): Boolean {
        return uuid.isBlank()
    }
}

@Serializable
data class PlayerOnlineStatus(val context: Player, val player: Player, val online: Boolean, val discovery: Boolean)

@Serializable
data class ChatMessage(
    /**
     * The bot this message belongs to
     */
    val context: Player,
    val message: String,
    val sender: Player,
    /**
     * The recipient of the message.
     *
     * Only applicable to whispers
     */
    val recipient: Player?
) {
    fun isPM(): Boolean {
        return recipient != null
    }

    fun isBotMessage(): Boolean {
        return context == sender
    }

    /**
     * Construct a new [ChatMessage] in which the sender is the bot that received this message
     */
    fun reply(message: String, pm: PM = PM.SAME_CHANNEL): ChatMessage {
        return ChatMessage(
            context,
            message,
            context,
            pm.coalesceRecipient(this)
        )
    }

    enum class PM {
        SAME_CHANNEL, FORCE_PUBLIC, FORCE_PM;

        fun coalesceRecipient(chatMessage: ChatMessage): Player? {
            return when (this) {
                SAME_CHANNEL -> if (chatMessage.recipient != null) chatMessage.sender else null
                FORCE_PUBLIC -> null
                FORCE_PM -> chatMessage.sender
            }
        }
    }
}

@Serializable
data class Config(
    val broker: String?,
    val dbUrl: String?,
    val dbUser: String,
    val dbPassword: String,
)