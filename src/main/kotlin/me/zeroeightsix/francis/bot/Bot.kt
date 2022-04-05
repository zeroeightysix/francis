package me.zeroeightsix.francis.bot

import me.zeroeightsix.francis.ChatMessage
import me.zeroeightsix.francis.communicate.Emitter

interface Bot {

    fun respondTo(cm: ChatMessage, emitter: Emitter)
    fun schedule(cm: ChatMessage, emitter: Emitter)

}