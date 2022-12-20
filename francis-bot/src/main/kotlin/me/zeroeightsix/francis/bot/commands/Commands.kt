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
import me.zeroeightsix.francis.bot.commands.TimeoutCache.Companion.toHuman
import me.zeroeightsix.francis.communicate.Database
import me.zeroeightsix.francis.communicate.Database.getBalance
import me.zeroeightsix.francis.communicate.Database.getUUID
import me.zeroeightsix.francis.communicate.Database.prepare
import me.zeroeightsix.francis.communicate.Emitter
import me.zeroeightsix.francis.interpret
import org.slf4j.LoggerFactory
import registerAndAlias
import rootLiteral
import string
import java.sql.Connection
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object Commands : CommandDispatcher<Context>() {

    private val log = LoggerFactory.getLogger("commands")
    private val unknownPlayerException = SimpleCommandExceptionType(LiteralMessage("I don't know anyone by that name"))

    init {
        Balance; TopBalance; Send; Florida; Praise; Message; JoinMessage; Help; Sinner
    }

    data class Context(val message: ChatMessage, val bot: Bot, val emitter: Emitter) {
        fun reply(message: String, pm: ChatMessage.PM = ChatMessage.PM.SAME_CHANNEL) {
            bot.schedule(this.message.reply(message, pm), emitter)
        }
    }

    object Balance {
        init {
            registerAndAlias(rootLiteral("balance") {
                string("player") {
                    does { ctx ->
                        val recipientUsername: String = "player" from ctx

                        val balance = Database.connection.use { con ->
                            con.prepare("select balance from users where username=?", recipientUsername)
                                .executeQuery()
                                .run { if (next()) getInt("balance") else throw unknownPlayerException.create() }
                        }
                        ctx.source.reply("$recipientUsername has $balance prayers available.")

                        SINGLE_SUCCESS
                    }
                }

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

    object TopBalance {
        init {
            registerAndAlias(rootLiteral("topbal") {
                does {
                    data class Result(val username: String, val balance: Int)

                    val list = Database.connection.use { con ->
                        con.prepare("select username, balance from users order by balance desc limit 5")
                            .executeQuery()
                            .interpret<Result>()
                            .joinToString { (username, balance) -> "$username ($balance)" }
                    }

                    it.source.reply("Top balances are: $list")

                    SINGLE_SUCCESS
                }
            }, "topbalance")
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
        private val cache = TimeoutCache()
        private const val TIMEOUT = 12 * 60 * 60L // 12 hours

        init {
            registerAndAlias(rootLiteral("praise") {
                does { ctx ->
                    val sender = ctx.source.message.sender
                    // if the timeout is still in effect, quit
                    cache.getTimeout(sender.uuid, TIMEOUT)?.let {
                        throw "Too early! You need to wait ${it.toHuman()} before praising once more."()
                    }

                    // place a new timeout
                    cache.placeTimeout(sender.uuid)

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
        private var lastFlorida: Instant? = null
        private const val TIMEOUT = 20 * 60L

        init {
            this::class.java.classLoader.getResource("florida.txt")
                ?.readText()
                ?.lines()
                ?.let { lines ->
                    register(rootLiteral("florida") {
                        does { ctx: CommandContext<Context> ->
                            val now = Instant.now()
                            if (lastFlorida?.plusSeconds(TIMEOUT)?.isAfter(now) == true)
                                throw "Sorry, our editors are still working on the next headline."()
                            lastFlorida = now

                            val line = lines.random()
                            ctx.source.reply(line, ChatMessage.PM.FORCE_PUBLIC)

                            val counter = Database.connection.use { con ->
                                val uuid = ctx.source.message.sender.uuid
                                con.prepare(
                                    "insert into florida values (?, 1) on duplicate key update floridas=floridas+1",
                                    uuid
                                ).execute()
                                con.prepare("select floridas from florida where user=?", uuid)
                                    .executeQuery()
                                    .run { next(); getInt("floridas") }
                            }

                            ctx.source.reply(
                                "Thank you for spreading the news. Your florida counter is now at $counter.",
                                ChatMessage.PM.FORCE_PM
                            )

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

                                val c = con.incurCost(60, sender)

                                con.prepare(
                                    "insert into messages (sender, recipient, message) values (?, ?, ?)",
                                    sender,
                                    recipient,
                                    "message".from<String, Context>(ctx)
                                ).executeUpdate()

                                c
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

                val c = con.incurCost(260, ctx.source.message.sender.uuid)

                val statement = con.prepare("update users set join_message=? where uuid=?")
                statement.setString(1, message)
                statement.setString(2, recipient)
                statement.executeUpdate()

                c
            }
            ctx.source.reply("Thank you. The join message was ${if (message == null) "cleared" else "set"}. In order to cover operating costs, a fee of $cost prayers was deducted from your balance.")

            return SINGLE_SUCCESS
        }
    }

    object Help {
        init {
            registerAndAlias(rootLiteral("help") {
                does { ctx ->
                    ctx.source.reply(
                        "https://gist.github.com/zeroeightysix/74165b5a6cba1d1a37e30bd72158f54e",
                        ChatMessage.PM.FORCE_PM
                    )

                    SINGLE_SUCCESS
                }
            }, "h")
        }
    }

    object Sinner {
        private val timeoutCache = TimeoutCache()
        private const val TIMEOUT = 4 * 60 * 60L // 4 hours

        private fun createNormalMessage(cost: Int) = arrayOf(
            "Duly noted. An offering of $cost prayers was removed from your balance.",
            "We take your accusation seriously. In the form of expiation, you paid $cost prayers for this action.",
            "We appreciate your donation of $cost prayers. In return, we'll be wary of that player."
        ).random()

        private fun createLargeMessage(cost: Int) = arrayOf(
            "We appreciate your charitable donation of $cost prayers. The church will record this information carefully.",
            "This information is immensely helpful to us. Because of your large contribution of $cost prayers, we are sure to act upon this matter.",
            "Thank you for your lavish handout of $cost prayers. That player will surely be condemned."
        ).random()

        private fun createHugeMessage(cost: Int) = arrayOf(
            "Oh, my! $cost prayers? A gift of grace! Your complaint will be handled, immediately.",
            "The church is honoured to receive your gift of $cost prayers. I will take action immediately.",
            "Such benefaction! At the cost of $cost prayers, that player will definitely be dealt with."
        ).random()

        init {
            register(rootLiteral("sinner") {
                string("player") {
                    does { ctx ->
                        val sender = ctx.source.message.sender.uuid
                        timeoutCache.getTimeout(sender, TIMEOUT)?.let {
                            throw "Patience, young one. You may relay this information to me in ${it.toHuman()}."()
                        }
                        timeoutCache.placeTimeout(sender)

                        val recipient: PlayerID

                        val cost = Database.connection.use { con ->
                            recipient = con.getUUID("player" from ctx)
                                ?: throw unknownPlayerException.create()

                            val baseCost = min(max((con.getBalance(sender) * 0.1).toInt(), 50), 5000)
                            val cost = con.incurCost(baseCost, sender)

                            val strength = sqrt(1 - ((cost / 5000f) - 1).pow(2))
                            con.prepare(
                                "update users set faith=users.faith + (? - users.faith) * ? where uuid=?",
                                -1f,
                                strength,
                                recipient
                            ).executeUpdate()

                            cost
                        }

                        val message = when (cost) {
                            in 0..150 -> createNormalMessage(cost)
                            in 150..650 -> createLargeMessage(cost)
                            else -> createHugeMessage(cost)
                        }
                        ctx.source.reply(message)

                        SINGLE_SUCCESS
                    }
                }
            })
        }
    }

    private fun Connection.incurCost(baseCost: Int, player: PlayerID): Int {
        val (balance, faith) = prepare("select balance, faith from users where uuid=?", player)
            .executeQuery()
            .run { if (next()) getInt("balance") to getFloat("faith") else null }
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
