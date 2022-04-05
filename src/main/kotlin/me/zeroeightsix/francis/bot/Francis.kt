package me.zeroeightsix.francis.bot

import com.mojang.brigadier.exceptions.CommandSyntaxException
import me.zeroeightsix.francis.ChatMessage
import me.zeroeightsix.francis.Player
import me.zeroeightsix.francis.PlayerID
import me.zeroeightsix.francis.PlayerOnlineStatus
import me.zeroeightsix.francis.bot.commands.Commands
import me.zeroeightsix.francis.communicate.Database
import me.zeroeightsix.francis.communicate.Database.getJoinMessage
import me.zeroeightsix.francis.communicate.Database.prepare
import me.zeroeightsix.francis.communicate.Emitter
import org.slf4j.LoggerFactory
import java.time.Instant

class Francis : Bot {

    private val rewardTimeout = 5 * 60L // 5 minutes
    private val rewardedAt = HashMap<PlayerID, Instant>()
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
            // make sure this player is entered into the database
            // TODO: move to player event (player joins, or bot joins and announces all players)
            Database.assertUser(sender)

            val parse = Commands.parse(cm.message.substring(1), Commands.Context(cm, this, emitter))
            try {
                Commands.execute(parse)
            } catch (e: CommandSyntaxException) {
                schedule(
                    cm.reply(
                        e.message ?: "I'm sorry, but I couldn't process your command.",
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
        val now = Instant.now()
        if (!onlineStatus.online) {
            lastLeave[player.uuid] = now
        } else if (!onlineStatus.discovery) {
            // if it has been over 5 minutes since this player left
            if (lastLeave[player.uuid]?.plusSeconds(5 * 60L)?.isBefore(now) != false) {
                // Send their JM, if any
                Database.connection.use { con ->
                    con.getJoinMessage(player.uuid)?.let { jm ->
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
        }
    }

    override fun schedule(cm: ChatMessage, emitter: Emitter) {
        this.queue.add(cm, emitter)
    }

    private fun reward(sender: Player, reward: Faith.Reward) {
        // if the timeout is still in effect, quit
        if (rewardedAt[sender.uuid]?.plusSeconds(rewardTimeout)?.isAfter(Instant.now()) == true) return

        // place a new timeout
        rewardedAt[sender.uuid] = Instant.now()

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