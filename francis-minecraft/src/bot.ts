import {Channel} from "amqp-connection-manager";
import {Bot, BotOptions} from "mineflayer";

// const {mineflayer: mineflayerViewer} = require('prismarine-viewer')
import * as util from "util";

const mineflayer = require('mineflayer')
const amqp = require('amqp-connection-manager');
const fs = require('fs');

// read the config from config.json
// valid options are those provided by mineflayer, and 'broker' for rabbitmq
interface Config {
    broker: string,
}

let options: Config & Partial<BotOptions>;
try {
    options = JSON.parse(fs.readFileSync('config.json'));
} catch (err) {
    console.log("Missing or malformed configuration file?")
    console.error(err);
    process.exit(1);
}
// fix: default parser is broken and may crash the client
options.defaultChatPatterns = false;

const conn = amqp.connect([options.broker]);
const ch = conn.createChannel({
    json: true,
    async setup(ch: Channel) {
        await ch.assertExchange('chat', 'topic', {durable: true});
        await ch.assertExchange('players', 'direct', {durable: false})
        await ch.assertExchange('events', 'direct', {durable: false})

        let outgoing = await ch.assertQueue('', {exclusive: true, autoDelete: true});
        ch.bindQueue(outgoing.queue, 'chat', 'outgoing.*');
        ch.consume(outgoing.queue, onOutgoing, {noAck: true})

        let reconnect = await ch.assertQueue('', {exclusive: true, autoDelete: true});
        ch.bindQueue(reconnect.queue, 'events', 'connect');
        ch.consume(reconnect.queue, onConnect, {noAck: true})
    }
});
ch.waitForConnect().then(() => console.info(`Connected to broker on ${options.broker}`));

class Francis {
    bot: Bot;
    context!: Player;
    initialised: boolean;
    live: boolean = true;

    constructor(options: Config & Partial<BotOptions>) {
        const bot = mineflayer.createBot(options)
        this.initialised = false

        bot.once('spawn', () => {
            console.info({clientToken: bot._client.session?.clientToken, accessToken: bot._client.session?.accessToken})
            console.info("Initialising")

            this.initialised = true;

            bot.addChatPattern(
                'public',
                /^<(\w+)> (.*)$/,
                {parse: true, repeat: true}
            );
            bot.addChatPattern(
                'private',
                /^(\w+) whispers to you: (.*)$/,
                {parse: true, repeat: true}
            );
            bot.addChatPattern(
                'private',
                /^<--(\w+): (.*)$/,
                {parse: true, repeat: true}
            );
            bot.addChatPattern(
                'server',
                /^\[Server] (.*)$/,
                {parse: true, repeat: true}
            );

            ch.publish('events', 'connected', <ConnectMessage> {
                botId
            }).then(() => console.info(`Connected event emitted`));

            // this.viewer = mineflayerViewer(bot, {port: 3007, firstPerson: true})
        });

// noinspection TypeScriptValidateJSTypes
        bot.on('chat:public', async ([[username, message]]: [[string, string]]) => {
            await ch.publish('chat', 'incoming.public', this.newMessage(message, username));
        });
// noinspection TypeScriptValidateJSTypes
        bot.on('chat:private', async ([[username, message]]: [[string, string]]) => {
            await ch.publish('chat', 'incoming.private', this.newMessage(message, username, this.context.username));
        });
// noinspection TypeScriptValidateJSTypes
        bot.on('chat:server', async ([[message]]: [[string, string]]) => {
            await ch.publish('chat', 'incoming.private', this.newMessage(message, "Server", this.context.username));
            await ch.publish('chat', 'incoming.private', <ChatMessage> {
                message,
                sender: <Player> {
                    username: "Server",
                    uuid: "4dbd97e5-8487-429f-875d-3bb2860eb041"
                },
                context: this.context,
                recipient: null
            });
        });

        bot.on('playerJoined', async (player: Player) => {
            if (player.username === bot.username) {
                this.context = {
                    username: player.username,
                    uuid: player.uuid
                }
                console.info(`Context: `, this.context)
            }

            console.info("Player joined", {username: player.username, uuid: player.uuid});
            await ch.publish('players', 'join', {
                context: this.context,
                player: {username: player.username, uuid: player.uuid},
                online: true,
                discovery: !this.initialised
            });
        })
        bot.on('playerLeft', async (player: Player) => {
            console.info("Player left", {username: player.username, uuid: player.uuid})
            await ch.publish('players', 'leave', {
                context: this.context,
                player: {username: player.username, uuid: player.uuid},
                online: false,
                discovery: !this.initialised
            });
        })
        bot.on('kicked', async (reason: string, loggedIn: boolean) => {
            console.info("Kicked", {reason, loggedIn})

            this.live = false;

            await ch.publish('events', 'disconnected', <DisconnectedMessage> {
                botId,
                context: this.context,
                reason,
                loggedIn,
            });
        })
        bot.on('end', async (reason: string) => {
            console.info({reason})
        })

        this.bot = bot;
    }

    newMessage(message: string, sender: string, recipient: string | null = null): ChatMessage {
        return {
            context: this.context,
            message: message,
            sender: this.player(sender)!,
            recipient: this.player(recipient)
        }
    }

    player(username: string | null): Player | null {
        if (username) {
            return {
                username,
                uuid: this.bot.players[username]?.uuid ?? ''
            }
        }
        return null
    }
}

const botId = (Math.random() * Number.MAX_SAFE_INTEGER).toFixed()
let francis = new Francis(options)

interface Player {
    username: string,
    uuid: string,
}

interface ChatMessage {
    context: Player,
    message: string,
    sender: Player,
    recipient: Player | null,
}

interface ConnectMessage {
    botId: string,
}

interface DisconnectedMessage {
    botId: string,
    context: Player,
    reason: string,
    loggedIn: boolean
}

function onOutgoing(data: any) {
    const context = francis.context;
    const cm: ChatMessage = JSON.parse(data.content.toString());
    // is this for me?
    if (util.isDeepStrictEqual(context, cm.sender) && util.isDeepStrictEqual(context, cm.context)) {
        if (!francis.live) {
            console.error("refusing to send message: bot is disconnected")
            return;
        }

        // if a recipient is specified, we want to tell:
        if (cm.recipient) {
            francis.bot.chat(`/w ${cm.recipient.username} ${cm.message}`)
        } else {
            // otherwise, just toss it in public chat
            francis.bot.chat(cm.message);
        }
    }
}

function onConnect(data: any) {
    if (francis.live) return

    const cm: ConnectMessage = JSON.parse(data.content.toString());
    // is this for me?
    if (botId == cm.botId) {
        francis = new Francis(options)
    }
}