package consensus.messages

data class AbortMessage(
    val senderBallot: Int,
    val ballot: Int
)
