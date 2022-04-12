package me.zeroeightsix.francis.discord

import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.Charset

const val CHAT = "chat"
const val PLAYERS = "players"
const val EVENTS = "events"

class Broker(config: Config) {

    val log: Logger = LoggerFactory.getLogger("broker")
    val channel: Channel

    init {
        val factory = ConnectionFactory()
        val conn = factory.newConnection(config.broker ?: "amqp://0.0.0.0")
        channel = conn.createChannel()

        channel.exchangeDeclare(CHAT, "topic", true)
        channel.exchangeDeclare(PLAYERS, "direct", false)
        channel.exchangeDeclare(EVENTS, "direct", false)
    }

    inline fun <reified T> on(
        exchange: String,
        vararg routingKeys: String,
        crossinline handler: suspend (T) -> Unit
    ) {
        val chatQ = channel.queueDeclare().queue
        for (key in routingKeys) {
            channel.queueBind(chatQ, exchange, key)
        }
        channel.basicConsume(chatQ, true, DeliverCallback { _, delivery ->
            val message = try {
                Json.decodeFromString<T>(String(delivery.body, Charset.forName("UTF-8")))
            } catch (e: Exception) {
                log.error("Invalid ${T::class.simpleName} received", e)
                return@DeliverCallback
            }

            CoroutineScope(Dispatchers.Default).launch {
                handler(message)
            }
        }, CancelCallback {
            log.error("Queue $it died, time to shit your pants")
        })
    }

    inline fun <reified T> publish(
        exchange: String,
        routingKey: String,
        value: T
    ) {
        val json = Json.encodeToString(value).encodeToByteArray()
        this.channel.basicPublish(
            exchange,
            routingKey,
            null,
            json
        )
    }

}