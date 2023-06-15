package consensus.messages

data class DecideMessage(
    val senderBallot: Int,
    val decision: Int?
)
