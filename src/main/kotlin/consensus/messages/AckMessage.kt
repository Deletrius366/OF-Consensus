package consensus.messages

data class AckMessage(
    val senderBallot: Int,
    val ballot: Int
)