package consensus.messages

import java.time.LocalTime

data class AnswerMessage (
    val decision : Int?,
    val launchTime: LocalTime?,
    val decideTime: LocalTime?
)