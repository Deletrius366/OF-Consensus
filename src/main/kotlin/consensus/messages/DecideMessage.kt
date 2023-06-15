package consensus.messages

data class DecideMessage(
    val senderBallot: Int,
    val processId: Int,
    val decision: Int?
)
