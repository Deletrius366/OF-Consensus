package consensus

import akka.actor.ActorRef
import akka.actor.UntypedAbstractActor
import akka.event.Logging
import consensus.messages.*
import kotlin.random.Random

class Process(
    private val id: Int,
    private val processCount: Int,
    private val chanceToCrash: Double,
    private val processes: List<ActorRef>,
    private val auxActor: ActorRef
) : UntypedAbstractActor() {
    private val logger = Logging.getLogger(context.system, this)

    private var ballot = id - processCount
    private var proposal: Int = 0
    private var readBallot = 0
    private var imposeBallot = id - processCount
    private var estimate: Int? = null
    private var states: Array<Pair<Int?, Int>> = (0..processCount).map { null to defaultEstBallotValue }.toTypedArray()
    private var statesChanged: Array<Boolean> = Array(processCount) { false }
    private var decision: Int? = null

    private val defaultEstBallotValue = 0
    private var ackReceived = 0
    private var aborted = true

    private var crashed = false

    private var senderRef: ActorRef? = null

    override fun onReceive(message: Any?) {
//        if (id == 0 && message is DecideMessage)
//            logger.info("Received Decide message, decision: ${message.decision}")
        tryToCrash()

        if (crashed)
            return

        if (decision != null && message !is LaunchMessage) {
            return
        }

        when (message) {
            is LaunchMessage -> handleLaunchMessage(message)
            is ReadMessage -> handleReadMessage(message)
            is AbortMessage -> handleAbortMessage()
            is GatherMessage -> handleGatherMessage(message)
            is ImposeMessage -> handleImposeMessage(message)
            is AckMessage -> handleAckMessage()
            is DecideMessage -> handleDecideMessage(message)
        }
    }

    private fun handleImposeMessage(message: ImposeMessage) {
        if (readBallot > message.ballot || imposeBallot > message.ballot) {
            sender.tell(AbortMessage(message.senderBallot, message.ballot), self)
        } else {
            estimate = message.proposal
            imposeBallot = message.ballot
            sender.tell(AckMessage(message.senderBallot, message.ballot), self)
        }
    }

    private fun handleGatherMessage(message: GatherMessage) {
        val idx = actorIdx(sender)
        states[idx] = message.est to message.estBallot
        statesChanged[idx] = true

        if (isMajority(statesChanged.filter { it }.size)) {

            if (states.any { it.second > 0 }) {
                val maxState = states.maxOfWith(compareBy { it.second }) { it }
                proposal = maxState.first!!
            }
            initializeStates()
            sendAll(ImposeMessage(ballot, ballot, proposal))
        }
    }

    private fun handleAbortMessage() {
        aborted = true

        // self.tell(LaunchMessage(Random.nextInt(0, 2)), null)
    }

    private fun handleReadMessage(message: ReadMessage) {
        if (readBallot > message.ballot || imposeBallot > message.ballot) {
            sender.tell(AbortMessage(message.senderBallot, message.ballot), self)
        } else {
            readBallot = message.ballot
            sender.tell(GatherMessage(message.senderBallot, message.ballot, imposeBallot, estimate), self)
        }
    }

    private fun handleLaunchMessage(message: LaunchMessage) {
        if (sender != null)
            senderRef = sender

        // logger.info("Decision: $decision, Aborted: $aborted")
        if (decision != null) {
            return
        }

        if (aborted) {
            aborted = false

            proposal = message.proposal
            ballot += processCount
            initializeStates()

            sendAll(ReadMessage(ballot, ballot))
        }
    }

    private fun handleDecideMessage(message: DecideMessage) {
        if (decision == null) {
            // logger.info("Process $id decide ${message.decision}")
            auxActor.tell(DecideMessage(ballot, message.decision), self)
        }

        decision = message.decision
    }

    private fun handleAckMessage() {
        ackReceived++
        if (isMajority(ackReceived)) {
            sendAll(DecideMessage(ballot, proposal))
        }
    }

    private fun actorIdx(actor: ActorRef): Int {
        (0..processCount).forEach {
            if (processes[it] == actor)
                return it
        }
        throw IllegalArgumentException("Failed to find actor id")
    }

    private fun sendAll(message: Any) {
        processes.forEach {
            it.tell(message, self)
        }
    }

    private fun isMajority(value: Int): Boolean =
        value >= processCount / 2 + 1

    private fun initializeStates() {
        states = (0..processCount).map { null to defaultEstBallotValue }.toTypedArray()
        Array(processCount) { false }
    }

    private fun tryToCrash() {
        val r = Random.nextDouble(0.0, 1.0)
        if (r < chanceToCrash) {
            if (!crashed)
                logger.warning("Process crashed!")
            crashed = true
        }
    }

}