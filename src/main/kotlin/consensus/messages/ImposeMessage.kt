package consensus.messages

data class ImposeMessage (
    val senderBallot: Int,
    val ballot: Int,
    val proposal: Int,
)