package me.zeroeightsix.francis.bot.commands

import com.mojang.brigadier.Command.SINGLE_SUCCESS
import com.mojang.brigadier.CommandDispatcher
import does
import me.zeroeightsix.francis.ChatMessage
import me.zeroeightsix.francis.bot.Bot
import me.zeroeightsix.francis.bot.commands.Commands.Context
import me.zeroeightsix.francis.communicate.Emitter
import rootLiteral

object Commands : CommandDispatcher<Context>() {
    val hello = rootLiteral<Context>("hello") {
        does {
            it.source.reply("Hello!")

            SINGLE_SUCCESS
        }
    }

    init {
        register(hello)
    }

    data class Context(val message: ChatMessage, val bot: Bot, val emitter: Emitter) {
        fun reply(message: String, pm: ChatMessage.PM = ChatMessage.PM.SAME_CHANNEL) {
            return emitter.emitChat(this.message.reply(message, pm))
        }
    }
}
