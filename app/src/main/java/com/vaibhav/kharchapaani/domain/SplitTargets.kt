package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import java.util.Locale

/**
 * Who a quick-add line says it was shared with ("dinner 1800 with Rohan and Ankit", "petrol 500 Goa
 * trip"), found among this account's friends and groups on the phone. The AI and the phone's own
 * parser only hand over names as they were said; matching them to people happens here, so the
 * friend and group lists never have to leave the phone for it.
 *
 * Nothing is decided for the person: the sheet opens with this split filled in, and a name that
 * matched nobody, or more than one friend, is reported so the sheet can say so.
 */
object SplitTargets {

    data class Target(
        /** A group named in the line, or null. */
        val groupId: Long? = null,
        /** Friends named in the line, one id each. */
        val personIds: Set<Long> = emptySet(),
        /** Names that matched no friend, as they were written. */
        val unknownNames: List<String> = emptyList(),
        /** Names that matched more than one friend ("Rahul" with two Rahuls), as written. */
        val ambiguousNames: List<String> = emptyList(),
    )

    /** Words people put around a name that aren't part of it. */
    private val HONORIFICS = setOf("bhai", "bhaiya", "bro", "ji", "da", "di", "didi", "sir", "madam")

    /** The person adding the expense, who is in every split anyway. */
    private val SELF = setOf("me", "you", "myself", "i", "self")

    fun resolve(
        text: String,
        names: List<String>,
        people: List<PersonEntity>,
        groups: List<GroupEntity>,
    ): Target {
        val group = groupIn(text, groups)
        val groupName = group?.name?.let(::normalise)

        val personIds = linkedSetOf<Long>()
        val unknown = mutableListOf<String>()
        val ambiguous = mutableListOf<String>()
        names.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy(::normalise).forEach { name ->
            val key = normalise(name)
            if (key.isEmpty() || key in SELF || key == groupName) return@forEach
            val matches = matchesFor(key, people)
            when (matches.size) {
                0 -> unknown += name
                1 -> personIds += matches.single().id
                else -> ambiguous += name
            }
        }
        return Target(group?.id, personIds, unknown, ambiguous)
    }

    /**
     * What the sheet says when the split isn't everything the line asked for, or null when it is.
     * [leftOut] are friends found but not able to join [groupName]'s split.
     */
    fun notice(target: Target, leftOut: List<String> = emptyList(), groupName: String? = null): String? {
        val parts = buildList {
            target.ambiguousNames.forEach { add("More than one $it in your friends, so pick the right one in the split.") }
            if (target.unknownNames.isNotEmpty()) {
                add("Couldn't find ${spoken(target.unknownNames)} in your friends, so they're not in the split.")
            }
            if (leftOut.isNotEmpty() && groupName != null) {
                add("${spoken(leftOut)} ${if (leftOut.size == 1) "isn't" else "aren't"} in $groupName, so they're not in its split.")
            }
        }
        return parts.joinToString(" ").ifEmpty { null }
    }

    /** "Priya", "Priya and Sam", "Priya, Sam and Neha". */
    private fun spoken(names: List<String>): String =
        if (names.size == 1) names.single() else names.dropLast(1).joinToString(", ") + " and " + names.last()

    /** A whole name first; a first name only when it's the only friend with it. */
    private fun matchesFor(key: String, people: List<PersonEntity>): List<PersonEntity> {
        val whole = people.filter { normalise(it.name) == key }
        if (whole.isNotEmpty()) return whole
        if (' ' in key) return emptyList()
        return people.filter { normalise(it.name).substringBefore(' ') == key }
    }

    /** The longest active group whose whole name appears in the line, word for word. */
    private fun groupIn(text: String, groups: List<GroupEntity>): GroupEntity? {
        val line = " ${words(text).joinToString(" ")} "
        return groups
            .filter { !it.isArchived }
            .map { it to normalise(it.name) }
            .filter { (_, name) -> name.isNotEmpty() && line.contains(" $name ") }
            .maxByOrNull { (_, name) -> name.length }
            ?.first
    }

    private fun normalise(name: String): String =
        words(name).filter { it !in HONORIFICS }.joinToString(" ")

    private fun words(text: String): List<String> =
        text.lowercase(Locale.ROOT).split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
}
