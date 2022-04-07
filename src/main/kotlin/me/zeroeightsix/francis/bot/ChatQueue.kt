package me.zeroeightsix.francis.bot

import me.zeroeightsix.francis.ChatMessage
import me.zeroeightsix.francis.communicate.Emitter
import org.slf4j.LoggerFactory
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread
import kotlin.math.max

// We apply spamming rules as according to https://github.com/C4K3/NoSpam/blob/faaa18545f3d73cc1a0c215f88e1304e45bfd6d1/src/SpamHandler.java#L70
// the bot may not send:
// >2 messages in a .4 second frame
// >3 messages in a 1-second frame
// >7 messages in a 15-second frame
// for wiggle room, time frames are adjusted by +.3 seconds.
// it is necessary to space messages in order for the bot to never be muted.
internal class ChatQueue {
    // oldest first
    private val messageTimes = ArrayDeque<Long>()
    private val queue = LinkedBlockingQueue<Pair<ChatMessage, Emitter>>()
    private var started = false

    fun start() {
        if (started) throw RuntimeException("Refusing to start chat queue worker twice")
        started = true
        thread(name = "Chat queue worker") {
            val log = LoggerFactory.getLogger("queue-worker")

            while (true) {
                val (message, emitter) = queue.take()

                // purge messages >15.3 seconds
                val now = System.currentTimeMillis()
                messageTimes.removeAll { now - it > 15300 }

                // if there are no messages to worry about, don't bother calculating:
                if (messageTimes.isEmpty()) {
                    messageTimes.addFirst(System.currentTimeMillis())
                    emitter.emitChat(message)
                    continue
                }

                var short = 0
                var shortWindow: Long = 0
                var med = 0
                var medWindow: Long = 0

                val long = messageTimes.size
                val longWindow = 15300 - (now - messageTimes.first())

                messageTimes.forEach {
                    val duration = now - it
                    if (duration < 400 + 300) {
                        short++
                        if (shortWindow == 0L) shortWindow = 400 + 300 - duration
                    }
                    if (duration < 1000 + 300) {
                        med++
                        if (medWindow == 0L) medWindow = 1000 + 300 - duration
                    }
                }

                // calculate the least amount of time we must wait in order to escape the spam window
                var wait = 0L
                if (short >= 2)
                    wait = shortWindow
                if (med >= 3)
                    wait = max(wait, medWindow)
                if (long >= 7)
                    wait = max(wait, longWindow)

                if (wait != 0L) {
                    log.info(
                        "Wait duration: $wait (" + when (wait) {
                            shortWindow -> "short"
                            medWindow -> "med"
                            longWindow -> "long"
                            else -> "other"
                        } + ")"
                    )
                }

                Thread.sleep(wait)

                messageTimes.addFirst(System.currentTimeMillis())
                emitter.emitChat(message)
            }
        }
    }

    fun add(message: ChatMessage, emitter: Emitter) {
        queue.add(message to emitter)
    }
}