package me.zeroeightsix.francis.bot

import me.zeroeightsix.francis.ChatMessage
import kotlin.math.log10

fun reward(message: ChatMessage, faith: Float): Pair<Int, Float> {
    val text = message.message.lowercase()
    if (text.contains("hate francis")
        || text.contains("francis died")
        || text.contains("francis is dead")
        || text.contains("killed francis")
        || (text.contains("francis") && text.contains("is dead"))
    ) {
        return (0 to demote(faith))
    }
    if (text.contains("francis")
        && (text.contains("praise") || text.contains("love") || text.contains("god"))
    ) {
        return (3 to promote(faith))
    }

    return (0 to 0f)
}

// Calculates a discount, which is a percentage ranging from ]-infinity; 0.4].
fun calculate_discount(faith: Float): Float {
    // Discounts are created using a logarithmic graph.
    // Good francillians get a reasonable, but not too large discount.
    // Wilburians get heavy negative discounts, with true wilburians at -infinity.

    // Define a value 'd' which is equal to the maximum 'good' discount:
    // d = 0.4
    // We create a function h(x) which goes through (1, d) and (-1, infinity)
    // h(x) = d * log10(5x+5)
    //  (5x+5) because it's log10

    // We want people in the middle to get no discount, so we subtract h(0):
    // p(x) = h(x) - h(0)

    // And then we stretch the graph to go through (1, d) once more:
    // q(x) = p(x) * (d / p(1))

    // This produces the following formula:
    return (0.4f * log10(5f * faith + 5f) - 0.279588f) * 3.321928f
}

// Calculates a new faith value, in favour of the Francis side, returning the delta
fun promote(faith: Float): Float {
    return map_faith(1f, 0.1f, faith)
}

// Calculates a new faith value, in favour of the wilburian side, returning the delta
fun demote(faith: Float): Float {
    return map_faith(-1f, 0.15f, faith)
}

fun map_faith(to: Float, scale: Float, faith: Float): Float {
    return (to - faith) * scale
}