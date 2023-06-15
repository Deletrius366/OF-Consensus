package consensus.messages

import akka.actor.ActorRef

data class LaunchLeaderMessage(
    val leader : ActorRef
)
