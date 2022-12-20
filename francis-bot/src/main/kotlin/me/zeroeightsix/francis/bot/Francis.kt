package me.zeroeightsix.francis.bot

import com.mojang.brigadier.exceptions.CommandSyntaxException
import me.zeroeightsix.francis.ChatMessage
import me.zeroeightsix.francis.Player
import me.zeroeightsix.francis.PlayerID
import me.zeroeightsix.francis.PlayerOnlineStatus
import me.zeroeightsix.francis.bot.commands.Commands
import me.zeroeightsix.francis.bot.commands.TimeoutCache
import me.zeroeightsix.francis.communicate.Database
import me.zeroeightsix.francis.communicate.Database.getJoinMessage
import me.zeroeightsix.francis.communicate.Database.prepare
import me.zeroeightsix.francis.communicate.Emitter
import org.slf4j.LoggerFactory
import java.time.Instant

class Francis : Bot {

    private val rewardCache = TimeoutCache()
    private val rewardTimeout = 5 * 60L // 5 minutes
    private val lastLeave = HashMap<PlayerID, Instant>()
    private val log = LoggerFactory.getLogger("bot")
    private val queue = ChatQueue().apply {
        start()
    }

    override fun onChatMessage(cm: ChatMessage, emitter: Emitter) {
        // we're not interested in bots and people without UUIDs
        val sender = cm.sender
        if (sender.isFugitive() || cm.isBotMessage()) return

        // should this message be rewarded?
        Faith.reward(cm)?.let { reward ->
            reward(sender, reward)
        }

        if (cm.message.startsWith("#")) {
            val parse = Commands.parse(cm.message.substring(1), Commands.Context(cm, this, emitter))
            try {
                Commands.execute(parse)
            } catch (e: CommandSyntaxException) {
                // If nodes is empty, no root command was matched. We assume the user did not mean to use the bot.
                if (parse.context.nodes.isEmpty()) return

                val error = if (parse.exceptions.isEmpty()) e.message ?: "I'm sorry, but I couldn't process your command."
                else "Missing an argument. Use: #" +
                        parse.context.nodes.joinToString(separator = " ") { it.node.name } + " <" +
                        parse.context.nodes.last().node.children.joinToString(separator = "|") { it.name } + ">"

                schedule(
                    cm.reply(
                        error,
                        ChatMessage.PM.FORCE_PM
                    ),
                    emitter
                )
                e.printStackTrace()
            }
        }
    }

    override fun onPlayerOnlineStatus(onlineStatus: PlayerOnlineStatus, emitter: Emitter) {
        val player = onlineStatus.player

        // enter or update this player into the DB if necessary
        if (onlineStatus.online) Database.assertUser(player)

        val now = Instant.now()
        if (!onlineStatus.online) {
            lastLeave[player.uuid] = now
        } else if (!onlineStatus.discovery) {
            // if it has been over 5 minutes since this player left
            if (lastLeave[player.uuid]?.plusSeconds(5 * 60L)?.isBefore(now) != false) {
                // Send their JM, if any
                Database.connection.use { con ->
                    con.getJoinMessage(player.uuid)?.let { jm ->
                        val jm = if (jm.startsWith('/')) {
                            ".$jm"
                        } else {
                            jm
                        }
                        schedule(
                            ChatMessage(
                                onlineStatus.context,
                                jm,
                                onlineStatus.context,
                                null
                            ), emitter
                        )
                    }
                }
            }

            // fetch messages for this user
            Database.connection.use { con ->
                val set = con.prepare("select message from messages where delivered=0 and recipient=?", player.uuid)
                    .executeQuery()

                var empty = true
                while (set.next()) {
                    empty = false
                    val message = set.getString("message")
                    schedule(ChatMessage(onlineStatus.context, message, onlineStatus.context, player), emitter)
                }

                if (!empty)
                    con.prepare("update messages set delivered=1 where recipient=?", player.uuid).executeUpdate()
            }
        }
    }

    override fun schedule(cm: ChatMessage, emitter: Emitter) {
        this.queue.add(cm, emitter)
    }

    private fun reward(sender: Player, reward: Faith.Reward) {
        // if the timeout is still in effect, quit
        if (!rewardCache.tryPerform(sender.uuid, rewardTimeout)) return

        // award the player
        Database.connection.use {
            it.prepare(
                "update users set balance=users.balance+?, faith=users.faith + (? - users.faith) * ? where uuid=?",
                reward.prayers,
                reward.shiftTo,
                reward.strength,
                sender.uuid
            ).executeUpdate()
        }

        log.info("Rewarded $sender using $reward")
    }

}