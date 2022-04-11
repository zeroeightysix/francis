package me.zeroeightsix.francis.bot

import me.zeroeightsix.francis.ChatMessage
import kotlin.math.log10

object Faith {
    private val hateRegex = Regex(
        """(i\s*(fucking)?\s*(hate|dislike|loathe|resent))\s*(francis|(ro)?bot([ _]francis)?|(the)?\s*(fucking)?\s*(bot|church))""",
        RegexOption.IGNORE_CASE
    )
    private val resentRegex = Regex(
        """fuck(ing)?\s*(((the)?\s*(ro)?bot)|francis|church)""",
        RegexOption.IGNORE_CASE
    )
    private val dissentPrefixRegex = Regex(
        """(annoying|irritating|useless|incompetent|weak|stupid|shitty)\s*((ro)?bot([ _]francis)?|church|francis)""",
        RegexOption.IGNORE_CASE
    )
    private val dissentSuffixRegex = Regex(
        """((ro)?bot|francis|church)(\s*(sucks|idiot|loser|useless|annoying|irritating|weak|stupid)|(.*)(is|it's) (useless|annoying|irritating|weak|stupid))""",
        RegexOption.IGNORE_CASE
    )

    fun reward(message: ChatMessage): Reward? {
        val text = message.message.lowercase()

        if (hateRegex.containsMatchIn(text)
            || resentRegex.containsMatchIn(text)
            || dissentPrefixRegex.containsMatchIn(text)
            || dissentSuffixRegex.containsMatchIn(text)
        ) {
            return Reward.BAD
        }
        if (text.contains("francis")
            && (text.contains("praise") || text.contains("love") || text.contains("god"))
        ) {
            return Reward.GOOD
        }

        return null
    }

    // Calculates a discount, which is a percentage ranging from ]-infinity; 0.4].
    fun calculateDiscount(faith: Float): Float {
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

    enum class Reward(val prayers: Int, val shiftTo: Float, val strength: Float) {
        GOOD(3, 1f, .1f),
        BAD(0, -1f, .15f);

        override fun toString(): String {
            return "Reward(prayers=$prayers, shiftTo=$shiftTo, strength=$strength)"
        }
    }
}