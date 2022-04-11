package me.zeroeightsix.francis.bot.commands

import me.zeroeightsix.francis.PlayerID
import java.time.Duration
import java.time.Instant

class TimeoutCache {

    private val backing = HashMap<PlayerID, Instant>()

    /**
     * Get the remaining time on the timeout if applicable
     *
     * @return `null` if there is no timeout or it has expired
     */
    fun getTimeout(user: PlayerID, timeoutSeconds: Long): Duration? {
        val expire = backing[user]?.plusSeconds(timeoutSeconds) ?: return null
        val now = Instant.now()
        if (expire.isAfter(now))
            return Duration.between(now, expire)
        backing.remove(user)
        return null
    }

    fun placeTimeout(user: PlayerID) {
        backing[user] = Instant.now()
    }

    /**
     * Try performing an action with a timeout of [timeoutSeconds].
     *
     * @return `false` if the player is still timed out. `true` if there was no timeout, but a new one was placed.
     */
    fun tryPerform(user: PlayerID, timeoutSeconds: Long): Boolean {
        if (getTimeout(user, timeoutSeconds) != null) return false
        placeTimeout(user)
        return true
    }

    companion object {
        /**
         * Format into a short, human-readable format.
         *
         * Examples:
         * - `1 day and 5 hours`
         * - `5 hours` (minutes is 0)
         * - `10 hours and 30 minutes`
         * - `30 minutes` (seconds are omitted)
         * - `12 seconds`
         */
        fun Duration.toHuman(): String {
            fun format(type: String, amount: Int)
                    = "$amount $type" + (if (amount != 1) "s" else "")

            val days = toDaysPart().toInt()
            val hours = toHoursPart()
            if (days != 0)
                return format("day", days) + if (hours != 0) " and " + format("hour", hours) else ""
            val minutes = toMinutesPart()
            if (hours != 0)
                return format("hour", hours) + if (minutes != 0) " and " + format("minute", minutes) else ""
            if (minutes != 0)
                return format("minute", minutes)
            return format("second", toSecondsPart())
        }
    }

}