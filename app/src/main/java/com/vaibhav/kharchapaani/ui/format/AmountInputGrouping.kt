package com.vaibhav.kharchapaani.ui.format

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Show inserted commas without changing the amount or counting them as editable digits. */
internal object AmountInputGrouping : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val grouped = Money.groupInput(text.text)
        if (grouped == text.text) return TransformedText(text, OffsetMapping.Identity)

        val displayOffsets = IntArray(text.length + 1)
        val inputOffsets = IntArray(grouped.length + 1)
        var inputOffset = 0
        for (displayIndex in grouped.indices) {
            val nextDisplayOffset = displayIndex + 1
            if (inputOffset < text.length && grouped[displayIndex] == text[inputOffset]) {
                inputOffset++
                displayOffsets[inputOffset] = nextDisplayOffset
            }
            inputOffsets[nextDisplayOffset] = inputOffset
        }

        return TransformedText(AnnotatedString(grouped), object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = displayOffsets[offset]
            override fun transformedToOriginal(offset: Int): Int = inputOffsets[offset]
        })
    }
}
