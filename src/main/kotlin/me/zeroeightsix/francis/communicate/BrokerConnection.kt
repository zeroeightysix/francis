package me.zeroeightsix.francis.communicate

import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.zeroeightsix.francis.bot.Bot
import me.zeroeightsix.francis.ChatMessage
import me.zeroeightsix.francis.Config
import org.slf4j.LoggerFactory
import java.nio.charset.Charset

class BrokerConnection(config: Config, bot: Bot) : Emitter {

    private val chat = "chat"
    private val log = LoggerFactory.getLogger("broker")
    private val channel: Channel

    init {
        val factory = ConnectionFactory()
        val conn = factory.newConnection(config.broker ?: "amqp://0.0.0.0")
        channel = conn.createChannel()

        channel.exchangeDeclare(chat, "topic", true)
        val queueName = channel.queueDeclare().queue
        channel.queueBind(queueName, chat, "incoming.*")

        channel.basicConsume(queueName, true, DeliverCallback { _, delivery ->
            val message = try {
                Json.decodeFromString<ChatMessage>(String(delivery.body, Charset.forName("UTF-8")))
            } catch (e: Exception) {
                e.printStackTrace()
                return@DeliverCallback
            }

            log.info("${delivery.envelope.routingKey} $message")

            bot.respondTo(message, this)
        }, CancelCallback {})
    }

    override fun emitChat(message: ChatMessage) {
        channel.basicPublish(
            chat,
            "outgoing.public",
            null,
            Json.encodeToString(message).encodeToByteArray()
        )
    }

}