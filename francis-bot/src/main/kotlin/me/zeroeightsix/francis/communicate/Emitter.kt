package me.zeroeightsix.francis.communicate

import me.zeroeightsix.francis.ChatMessage

interface Emitter {

    fun emitChat(message: ChatMessage)

}