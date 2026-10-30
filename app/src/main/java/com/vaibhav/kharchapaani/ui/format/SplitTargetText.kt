package com.vaibhav.kharchapaani.ui.format

import android.content.Context
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.domain.SplitTargets

/** Display-only counterpart: matching names to friends remains in SplitTargets. */
fun SplitTargets.Target.displayNotice(context: Context, leftOut: List<String> = emptyList(), groupName: String? = null): String? {
    fun spoken(names: List<String>): String = if (names.size == 1) names.single() else
        context.getString(R.string.split_notice_join, names.dropLast(1).joinToString(context.getString(R.string.split_notice_separator)), names.last())
    val parts = buildList {
        ambiguousNames.forEach { add(context.getString(R.string.split_notice_ambiguous, it)) }
        if (unknownNames.isNotEmpty()) add(context.getString(R.string.split_notice_unknown, spoken(unknownNames)))
        if (leftOut.isNotEmpty() && groupName != null) add(context.resources.getQuantityString(
            R.plurals.split_notice_left_out, leftOut.size, spoken(leftOut), groupName))
    }
    return parts.joinToString(" ").ifEmpty { null }
}
