package com.vaibhav.kharchapaani.domain

/**
 * One colour per person, distinct from everyone else's, used everywhere that person appears.
 *
 * The group donut showed three people in three shades of the same orange, which is the visible end
 * of two separate faults:
 *
 * 1. `abs(profileId.hashCode() % size)` picked the slot. Kotlin's `%` takes the sign of its
 *    dividend, so a negative hash gives a negative remainder and `abs` then folds it onto the *same*
 *    slot as its positive twin — index 3 is reached by both `+3` and `-3`. On this account's four
 *    real profile IDs that is not a theoretical risk: two pairs collide outright.
 * 2. Nothing checked afterwards. A colour was chosen once, at insert, against no one, so two people
 *    could hold the same one for ever with nothing to notice or fix it.
 *
 * [respace] fixes the second by treating the people on a phone as a set rather than as rows:
 * whoever holds a colour first keeps it, and anyone who duplicates it moves to the nearest free
 * slot. Deterministic in id order, so it produces the same answer on every device and every run,
 * and it is the app's single mapping — the avatars, the member chips and the donut all read the
 * `avatarColor` it settles on.
 */
object PersonColours {

    /**
     * Eight hues about 40° apart around the wheel, none of them near the brand amber, which is
     * reserved for [MINE]. This is the list the add-a-friend picker offers, so a colour chosen by
     * hand and a colour assigned by sync come from the same set.
     */
    val palette: List<String> = listOf(
        "#F2D06B", "#C6F24C", "#86E37F", "#5BD6B0", "#70C1FF", "#A79BF5", "#E092E8", "#FF8FB0",
    )

    /** "You", in the brand amber. Never handed to anyone else. */
    const val MINE = "#FF7A3D"

    /** The slot a stable key belongs in. `floorMod`, not `abs(%)`, for the reason above. */
    fun stable(key: String): String = palette[Math.floorMod(key.hashCode(), palette.size)]

    /** Whether a stored value is a colour this app can actually draw. */
    fun isValid(hex: String?): Boolean = hex != null && HEX.matches(hex)

    /**
     * The colours [people] should have so that no two are alike, given the ones they have now.
     *
     * Returns only the people whose colour has to change, so a repair pass writes as few rows as it
     * can and a phone with no collisions writes none at all.
     *
     * @param people every person on this phone, in any order. Their [PersonColour.key] is what a
     *   colour is derived from when they have none worth keeping.
     */
    fun respace(people: List<PersonColour>, myHue: Int? = null): Map<Long, String> {
        val changes = mutableMapOf<Long, String>()
        // Everyone is placed in the shade field (Hues.Slot): hue across, three shades down. A chosen
        // colour sits in the middle row, exactly as chosen, since every other phone shows it too.
        val taken = mutableListOf<Hues.Slot>()
        myHue?.let { taken += Hues.Slot(it, 1) }
        for (person in people.filter { it.chosenHue != null }) {
            val hex = Hues.hex(person.chosenHue!!)
            taken += Hues.Slot(person.chosenHue, 1)
            if (hex != person.colorHex) changes[person.id] = hex
        }

        // Oldest first, so the person who has had a colour longest is the one who keeps it and a
        // new arrival is the one who moves. Ties on id, so the result never depends on input order.
        for (person in people.filter { it.chosenHue == null }.sortedWith(compareBy({ it.createdAt }, { it.id }))) {
            val current = person.colorHex.takeIf { isValid(it) && it != MINE }
            val wanted = current ?: stable(person.key)
            val slot = Hues.slotOf(wanted)
            val settled = if (slot != null && Hues.isFreeSlot(slot, taken)) {
                taken += slot
                wanted
            } else {
                // The field holds about 90; past that someone has to share, and keeps what they had.
                Hues.firstFreeSlot(person.key, taken)?.also { taken += it }?.hex ?: wanted
            }
            if (settled != person.colorHex) changes[person.id] = settled
        }
        return changes
    }

    private val HEX = Regex("^#[0-9A-Fa-f]{6}$")
}

/** What [PersonColours.respace] needs to know about one person. */
data class PersonColour(
    val id: Long,
    /** Their profile ID when they have one, else something stable about them, like their name. */
    val key: String,
    val colorHex: String,
    val createdAt: Long,
    /**
     * Their own chosen colour, as a hue (design item 11): the same on every phone, so it's never
     * moved here. Everyone else fits around it.
     */
    val chosenHue: Int? = null,
)
