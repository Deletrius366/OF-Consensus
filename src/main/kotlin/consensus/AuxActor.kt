package consensus

import akka.actor.UntypedAbstractActor
import akka.event.Logging
import consensus.messages.AnswerMessage
import consensus.messages.DecideMessage
import consensus.messages.LaunchLeaderMessage
import consensus.messages.LaunchMessage
import java.time.LocalTime
import java.time.LocalTime.now
import kotlin.math.log
import kotlin.random.Random

class AuxActor(private val processCount : Int) : UntypedAbstractActor() {
    private val logger = Logging.getLogger(context.system, this)

    private var decision : Int? = null
    private var launchTime : LocalTime? = null
    private var decideTime : LocalTime? = null
    private var decisionCount : Int = 0

    override fun onReceive(message: Any?) {
        when(message) {
            is LaunchLeaderMessage -> handleLaunchMessage(message)
            is DecideMessage -> handleDecideMessage(message)
        }
    }

    private fun handleDecideMessage(message: DecideMessage) {
        if (decideTime == null)
            decideTime = now()

        if (decision != null) {
            // logger.warning("Received decision from process ${message.processId}")

            assert(decision == message.decision)

            decisionCount++
            if (decisionCount == processCount) {
                logger.warning("All decisions received!")
            }
            if (decisionCount > processCount) {
                logger.error("Received extra decisions!")
            }
        }


        decision = message.decision
    }

    private fun handleLaunchMessage(message: LaunchLeaderMessage) {
        if (launchTime == null)
            launchTime = now()

        sender.tell(AnswerMessage(decision, launchTime, decideTime), self)

        if (decision == null) {
            message.leader.tell(LaunchMessage(Random.nextInt(0, 2)), self)
        }

    }
}