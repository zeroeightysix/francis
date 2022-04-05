package me.zeroeightsix.francis.bot

import me.zeroeightsix.francis.ChatMessage
import me.zeroeightsix.francis.PlayerOnlineStatus
import me.zeroeightsix.francis.communicate.Emitter

interface Bot {

    fun onChatMessage(cm: ChatMessage, emitter: Emitter)
    fun onPlayerOnlineStatus(onlineStatus: PlayerOnlineStatus, emitter: Emitter)
    fun schedule(cm: ChatMessage, emitter: Emitter)

}