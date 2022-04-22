package me.zeroeightsix.francis.discord

import dev.minn.jda.ktx.*
import dev.minn.jda.ktx.interactions.button
import dev.minn.jda.ktx.interactions.slash
import dev.minn.jda.ktx.interactions.updateCommands
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.zeroeightsix.francis.discord.Database.prepare
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.ShutdownEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.utils.MarkdownSanitizer.escape
import net.dv8tion.jda.api.utils.MarkdownUtil
import net.dv8tion.jda.api.utils.MarkdownUtil.bold
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration

val disconnects = HashMap<Long, Message>()
var lastContext: Player? = null
var lastId: Long? = null

val players = HashSet<String>()

val linkCode = HashMap<String, Long>()

fun main() {
    val config = Json.decodeFromString<Config>(Files.readString(Path.of("config.json")))
    val log = LoggerFactory.getLogger("discord")

    Database.connect(config)

    val jda = JDABuilder.createLight(config.discordToken).injectKTX().build()

    jda.updateCommands {
        slash("connect", "Instruct the bot to (re)connect to the server in case the button doesn't work")
        slash("linkminecraft", "Link your minecraft account with discord")
        slash("players", "Show the online players")
    }.queue()

    lateinit var textChannel: TextChannel
    jda.listener<ReadyEvent> {
        textChannel = jda.getChannel(config.channel) ?: run {
            log.error("channel from config does not exist")
            jda.shutdown()
            return@listener
        }

        log.info("Ready up!")
    }
    jda.awaitReady()

    log.info("Connecting to broker.")
    val broker = Broker(config)

    jda.listener<ShutdownEvent> {
        log.info("Goodbye")
    }

    jda.listener<SlashCommandInteractionEvent> {
        when (it.name) {
            "connect" -> {
                val id = lastId
                if (id != null) {
                    it.reply("Okay, I'll try connecting. If the bot is already online, this will do nothing.")
                        .setEphemeral(true).await()

                    broker.publish(EVENTS, "connect", ConnectMessage(id))
                } else {
                    it.reply("I'm sorry, but it looks like the bot hasn't identified itself to me. Please contact 086.")
                        .setEphemeral(true).await()
                }
            }
            "linkminecraft" -> {
                val code = (0..6).joinToString(separator = "") { ('0'..'Z').random().toString() }
                linkCode[code] = it.user.idLong

                it.reply("Whisper the in-game bot the following code to link your accounts: `$code`").setEphemeral(true)
                    .await()
            }
            "players" -> {
                val width = players.maxOf { n -> n.length } + 1
                val table = players.chunked(2).joinToString("\n") { p ->
                    p[0].padEnd(width) + (p.getOrNull(1) ?: "")
                }

                it.replyEmbeds(Embed {
                    title = "${players.size} players"
                    description = MarkdownUtil.codeblock(table)
                })
                    .setEphemeral(true).await()
            }
        }
    }

    jda.listener<MessageReceivedEvent> { event ->
        val ctx = lastContext ?: return@listener
        val member = event.member ?: return@listener
        if (event.channel.idLong == textChannel.idLong && member.roles.any { it.idLong == config.chatRole }) {
            event.message.delete().await()

            val username = Database.connection.use {
                it.prepare("select username from users where discord=?", member.idLong).executeQuery()
                    .run { if (next()) getString("username") else null }
            } ?: run {
                member.user.openPrivateChannel().await().sendMessage(
                        """
                        You tried sending a message in the chat log, and do have the authority to send messages, but haven't linked your discord user to minecraft yet.
                        Do so by using the `/linkminecraft` command on discord. You will get further instructions on how to link your accounts.
                    """.trimIndent()
                    ).await()
                return@listener
            }

            broker.publish(
                CHAT, "outgoing.public", ChatMessage(
                    ctx, "($username) ${event.message.contentDisplay.replace('§', '&')}", ctx, null
                )
            )
        }
    }

    broker.on<ChatMessage>(CHAT, "incoming.public") { cm ->
        val username = escape(cm.sender.username.replace("_", "\\_"))
        val message = escape(cm.message.replace("_", "\\_"))
        lastContext = cm.context
        textChannel.sendMessage("<${bold(username)}> $message").allowedMentions(emptyList()).await()
    }

    broker.on<ChatMessage>(CHAT, "incoming.private") { cm ->
        val member = linkCode[cm.message] ?: return@on

        Database.connection.use {
            it.prepare("update users set discord=NULL where discord=?", member).executeUpdate()
            it.prepare("update users set discord=? where uuid=?", member, cm.sender.uuid).executeUpdate()
        }

        broker.publish(
            CHAT, "outgoing.private", ChatMessage(
                cm.context, "Cool! Your discord and minecraft accounts are now linked.", cm.context, cm.sender
            )
        )

        linkCode.remove(cm.message)
    }

    broker.on<DisconnectedMessage>(EVENTS, "disconnected") { dm ->
        players.clear()
        lastId = dm.botId

        val button = jda.button(
            style = ButtonStyle.PRIMARY, label = "Reconnect",
//            emoji = Emoji.fromUnicode("U+1F501"),
            expiration = Duration.INFINITE
        ) {
            log.info("Reconnect button pressed")
            val reconnectingMessage = Message(embed = EmbedBuilder {
                color = 0xff00ff
                title = "Reconnecting..."
            }.build())

            it.editMessage(reconnectingMessage).setActionRow(it.button.asDisabled()).await()

            log.info("Sending connect")
            broker.publish(EVENTS, "connect", ConnectMessage(dm.botId))
        }

        val kickedEmbed = EmbedBuilder {
            color = 0xff0044
            title = if (dm.loggedIn) {
                "Kicked! :slight_frown:"
            } else {
                "Couldn't reconnect. :tired_face:"
            }
        }.build()

        disconnects[dm.botId] = textChannel.sendMessageEmbeds(kickedEmbed).setActionRow(button).await()
    }

    broker.on<ConnectMessage>(EVENTS, "connected") { cm ->
        val message = disconnects[cm.botId]
        val embed = EmbedBuilder {
            color = 0x009dff
            title = "Connection established :slight_smile:"
        }.build()

        (message?.editMessage(Message {
            this.embed = embed
        }) ?: textChannel.sendMessageEmbeds(embed)).await()

        disconnects.remove(cm.botId)
    }

    broker.on<PlayerOnlineStatus>(PLAYERS, "join", "leave") { status ->
        if (status.online) {
            players.add(status.player.username)
        } else {
            players.remove(status.player.username)
        }

        if (status.discovery) return@on

        val username = escape(status.player.username.replace("_", "\\_"))
        val word = if (status.online) "joined" else "left"

        textChannel.sendMessage(MarkdownUtil.italics("$username $word the game")).await()
    }
}

typealias PlayerID = String

@Serializable
data class PlayerOnlineStatus(val context: Player, val player: Player, val online: Boolean, val discovery: Boolean)

@Serializable
data class Player(val username: String, val uuid: PlayerID)

@Serializable
data class ChatMessage(
    /**
     * The bot this message belongs to
     */
    val context: Player, val message: String, val sender: Player,
    /**
     * The recipient of the message.
     *
     * Only applicable to whispers
     */
    val recipient: Player?
)

/**
 * Instruct a bot to attempt (re)connecting.
 */
@Serializable
data class ConnectMessage(val botId: Long)

@Serializable
data class DisconnectedMessage(val botId: Long, val context: Player? = null, val reason: String, val loggedIn: Boolean)

@Serializable
data class Config(
    val broker: String?,
    val discordToken: String,
    val channel: Long,
    val chatRole: Long,
    val dbUrl: String?,
    val dbUser: String,
    val dbPassword: String,
)