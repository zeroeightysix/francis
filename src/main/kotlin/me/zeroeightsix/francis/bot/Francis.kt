package me.zeroeightsix.francis.bot

import me.zeroeightsix.francis.ChatMessage
import me.zeroeightsix.francis.communicate.Emitter

class Francis : Bot {

    override fun respondTo(message: ChatMessage, emitter: Emitter) {
        // we're not interested in people without UUIDs
        if (message.sender.isFugitive()) return

        if (message.message == "beep") {
            emitter.emitChat(message.reply("boop"))
        }
    }

}