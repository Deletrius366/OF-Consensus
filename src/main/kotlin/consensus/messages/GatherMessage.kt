package consensus.messages

data class GatherMessage(
    val senderBallot: Int,
    val ballot: Int,
    val estBallot: Int,
    val est: Int?
)
