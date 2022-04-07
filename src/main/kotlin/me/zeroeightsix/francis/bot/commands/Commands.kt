package me.zeroeightsix.francis.bot.commands

import com.mojang.brigadier.Command.SINGLE_SUCCESS
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import does
import from
import greedyString
import integer
import me.zeroeightsix.francis.ChatMessage
import me.zeroeightsix.francis.PlayerID
import me.zeroeightsix.francis.bot.Bot
import me.zeroeightsix.francis.bot.Faith
import me.zeroeightsix.francis.bot.commands.Commands.Context
import me.zeroeightsix.francis.communicate.Database
import me.zeroeightsix.francis.communicate.Database.getBalance
import me.zeroeightsix.francis.communicate.Database.getBalanceFaith
import me.zeroeightsix.francis.communicate.Database.getUUID
import me.zeroeightsix.francis.communicate.Database.prepare
import me.zeroeightsix.francis.communicate.Emitter
import org.slf4j.LoggerFactory
import registerAndAlias
import rootLiteral
import string
import java.sql.Connection
import java.time.Duration
import java.time.Instant

object Commands : CommandDispatcher<Context>() {

    private val log = LoggerFactory.getLogger("commands")
    private val unknownPlayerException = SimpleCommandExceptionType(LiteralMessage("I don't know anyone by that name"))

    init {
        Balance; Send; Florida; Praise; Message; JoinMessage; Help
    }

    data class Context(val message: ChatMessage, val bot: Bot, val emitter: Emitter) {
        fun reply(message: String, pm: ChatMessage.PM = ChatMessage.PM.SAME_CHANNEL) {
            bot.schedule(this.message.reply(message, pm), emitter)
        }
    }

    object Balance {
        init {
            registerAndAlias(rootLiteral("balance") {
                does {
                    val balance = Database.connection.use { con ->
                        con.getBalance(it.source.message.sender.uuid)
                    }
                    it.source.reply("You have $balance prayers available.")

                    SINGLE_SUCCESS
                }
            }, "b", "bal")
        }
    }

    object Send {
        init {
            registerAndAlias(rootLiteral("send") {
                string("player") {
                    integer("amount") {
                        does { ctx ->
                            val sender = ctx.source.message.sender
                            val amount: Int = "amount" from ctx
                            val recipientUsername: String = "player" from ctx

                            if (amount < 1)
                                throw "You must send at least 1 prayer."()

                            Database.connection.use { con ->
                                if (con.getBalance(sender.uuid) < amount) {
                                    throw "The provided amount exceeds your available balance."()
                                }

                                val recipient = con.getUUID(recipientUsername)
                                    ?: throw unknownPlayerException.create()

                                if (sender.uuid == recipient) throw "You can't send prayers to yourself!"()

                                con.autoCommit = false
                                val stmt = con.prepare("update users set balance=users.balance+? where uuid=?")
                                stmt.setInt(1, -amount)
                                stmt.setString(2, sender.uuid)
                                stmt.execute()

                                stmt.setInt(1, +amount)
                                stmt.setString(2, recipient)
                                stmt.execute()
                                con.commit()
                                con.autoCommit = true
                            }

                            ctx.source.reply("Sent $amount prayers to $recipientUsername!")

                            SINGLE_SUCCESS
                        }
                    }
                }
            }, "pay")
        }
    }

    object Praise {
        private val rewardedAt = HashMap<PlayerID, Instant>()
        private const val rewardTimeout = 12 * 60 * 60L // 12 hours

        init {
            registerAndAlias(rootLiteral("praise") {
                does { ctx ->
                    val sender = ctx.source.message.sender
                    // if the timeout is still in effect, quit
                    rewardedAt[sender.uuid]?.plusSeconds(rewardTimeout)?.let { expire ->
                        val now = Instant.now()
                        if (expire.isAfter(now)) {
                            val time = Duration.between(now, expire)
                            throw "Too early! You need to wait ${time.toHours()} hours and ${time.toMinutesPart()} minutes before praising once more."()
                        }
                    }

                    // place a new timeout
                    rewardedAt[sender.uuid] = Instant.now()

                    // award the player
                    Database.connection.use { con ->
                        con.prepare(
                            "update users set balance=users.balance+55 where uuid=?",
                            sender.uuid
                        ).executeUpdate()
                    }

                    ctx.source.reply("Praise Francis! You were awarded 55 prayers for your devotion.")

                    log.info("Rewarded $sender for praising (55 prayers)")

                    SINGLE_SUCCESS
                }
            }, "p")
        }
    }

    object Florida {
        init {
            this::class.java.classLoader.getResource("florida.txt")
                ?.readText()
                ?.lines()
                ?.let { lines ->
                    register(rootLiteral("florida") {
                        does { ctx: CommandContext<Context> ->
                            val cost = incurCost(90, ctx.source.message.sender.uuid)

                            val line = lines.random()
                            ctx.source.reply(line, ChatMessage.PM.FORCE_PUBLIC)
                            ctx.source.reply("Thank you for spreading the news. An editorial fee of $cost prayers was deducted from your balance.", ChatMessage.PM.FORCE_PM)

                            SINGLE_SUCCESS
                        }
                    })
                } ?: log.error("Failed to load florida.txt")
        }
    }

    object Message {
        init {
            registerAndAlias(rootLiteral("message") {
                string("player") {
                    greedyString("message") {
                        does { ctx ->
                            val sender = ctx.source.message.sender.uuid
                            val cost = Database.connection.use { con ->
                                val recipient = con.getUUID("player" from ctx)
                                    ?: throw unknownPlayerException.create()

                                con.prepare(
                                    "insert into messages (sender, recipient, message) values (?, ?, ?)",
                                    sender,
                                    recipient,
                                    "message".from<String, Context>(ctx)
                                ).executeUpdate()

                                con.incurCost(60, sender)
                            }
                            ctx.source.reply("Thank you. Your message will be passed on anonymously. A processing fee of $cost prayers was deducted from your balance.")

                            SINGLE_SUCCESS
                        }
                    }
                }
            }, "w")
        }
    }

    object JoinMessage {
        init {
            registerAndAlias(rootLiteral("joinmessage") {
                string("player") {
                    greedyString("message") {
                        does {
                            setMessage(it, "message" from it)
                        }
                    }
                    does {
                        setMessage(it, null)
                    }
                }
            }, "jm")
        }

        private fun setMessage(ctx: CommandContext<Context>, message: String?): Int {
            val cost = Database.connection.use { con ->
                val recipient = con.getUUID("player" from ctx)
                    ?: throw unknownPlayerException.create()

                val statement = con.prepare("update users set join_message=? where uuid=?")
                statement.setString(1, message)
                statement.setString(2, recipient)
                statement.executeUpdate()

                con.incurCost(260, ctx.source.message.sender.uuid)
            }
            ctx.source.reply("Thank you. The join message was ${if (message == null) "cleared" else "set"}. In order to cover operating costs, a fee of $cost prayers was deducted from your balance.")

            return SINGLE_SUCCESS
        }
    }

    object Help {
        init {
            registerAndAlias(rootLiteral("help") {
                does { ctx ->
                    ctx.source.reply("https://gist.github.com/zeroeightysix/74165b5a6cba1d1a37e30bd72158f54e", ChatMessage.PM.FORCE_PM)

                    SINGLE_SUCCESS
                }
            }, "h")
        }
    }

    private fun incurCost(baseCost: Int, player: PlayerID): Int {
        return Database.connection.use { con ->
            con.incurCost(baseCost, player)
        }
    }

    private fun Connection.incurCost(baseCost: Int, player: PlayerID): Int {
        val (balance, faith) = getBalanceFaith(player)
            ?: throw RuntimeException("User $player has no db entry")

        val cost = (baseCost.toFloat() * (1f - Faith.calculateDiscount(faith))).toInt()
        if (cost > balance) throw "You can't afford that! You are ${cost - balance} prayers (out of $cost prayers) short."()

        prepare("update users set balance=users.balance+? where uuid=?", -cost, player).execute()

        return cost
    }

    private operator fun String.invoke(): CommandSyntaxException {
        return SimpleCommandExceptionType(LiteralMessage(this)).create()
    }
}
