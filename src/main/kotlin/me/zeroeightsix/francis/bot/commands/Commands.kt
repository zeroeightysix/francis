package me.zeroeightsix.francis.bot.commands

import com.mojang.brigadier.Command.SINGLE_SUCCESS
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import does
import from
import integer
import me.zeroeightsix.francis.ChatMessage
import me.zeroeightsix.francis.bot.Bot
import me.zeroeightsix.francis.bot.commands.Commands.Context
import me.zeroeightsix.francis.communicate.Database
import me.zeroeightsix.francis.communicate.Database.getBalance
import me.zeroeightsix.francis.communicate.Database.getUUID
import me.zeroeightsix.francis.communicate.Database.prepare
import me.zeroeightsix.francis.communicate.Emitter
import registerAndAlias
import rootLiteral
import string

object Commands : CommandDispatcher<Context>() {
    init {
        Balance; Send
    }

    data class Context(val message: ChatMessage, val bot: Bot, val emitter: Emitter) {
        fun reply(message: String, pm: ChatMessage.PM = ChatMessage.PM.SAME_CHANNEL) {
            return emitter.emitChat(this.message.reply(message, pm))
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
            }, "b")
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

                            if (amount <= 1)
                                throw "You must send at least 1 prayer."()

                            Database.connection.use { con ->
                                if (con.getBalance(sender.uuid) < amount) {
                                    throw "The provided amount exceeds your available balance."()
                                }

                                val recipient = con.getUUID(recipientUsername)
                                    ?: throw "I don't know anyone by that name"()

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

    private operator fun String.invoke(): CommandSyntaxException {
        return SimpleCommandExceptionType(LiteralMessage(this)).create()
    }
}
