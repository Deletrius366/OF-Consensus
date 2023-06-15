package consensus.messages

data class ReadMessage(
    val senderBallot: Int,
    val ballot: Int
)