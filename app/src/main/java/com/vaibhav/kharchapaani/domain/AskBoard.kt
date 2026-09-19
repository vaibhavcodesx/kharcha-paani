package com.vaibhav.kharchapaani.domain

/** Where one question has got to. */
sealed interface AskState {

    /** Never asked, or cleared because the month changed. */
    data object Idle : AskState

    data object Asking : AskState

    data class Answered(val text: String) : AskState

    /** No answer came back. The question is still there to try again. */
    data object Failed : AskState
}

/**
 * The three Insights questions, each with its own result.
 *
 * Replaces a single answer string and a single `failed` boolean shared by all three. Sharing them
 * meant one question failing wiped the answer under every other chip, so a working "This month"
 * read as broken the moment "What to change" didn't come back — three separate requests presented
 * as one outcome. They are independent requests and are now independent results.
 *
 * It also means an answer already fetched is still there when you tap back to its chip, which costs
 * nothing, shows instantly, and doesn't spend another of the day's asks.
 */
data class AskBoard(
    /** Which chip is selected, and so whose result is on screen. */
    val selected: AiPrompt? = null,
    private val states: Map<AiPrompt, AskState> = emptyMap(),
) {

    fun stateOf(prompt: AiPrompt): AskState = states[prompt] ?: AskState.Idle

    val selectedState: AskState get() = selected?.let(::stateOf) ?: AskState.Idle

    /** True while any question is in flight, which is what disables the chips. */
    val isAsking: Boolean get() = states.values.any { it == AskState.Asking }

    fun asking(prompt: AiPrompt): AskBoard = copy(selected = prompt, states = states + (prompt to AskState.Asking))

    fun answered(prompt: AiPrompt, text: String): AskBoard = copy(states = states + (prompt to AskState.Answered(text)))

    fun failed(prompt: AiPrompt): AskBoard = copy(states = states + (prompt to AskState.Failed))

    /** Shows what this question already has, without asking again. */
    fun select(prompt: AiPrompt): AskBoard = copy(selected = prompt)

    /** Whether tapping this chip needs a request, or just needs showing. */
    fun needsAsking(prompt: AiPrompt): Boolean = stateOf(prompt).let { it is AskState.Idle || it is AskState.Failed }

    /** Answers belong to the month they were asked about; a new month starts over. */
    fun cleared(): AskBoard = AskBoard()
}
