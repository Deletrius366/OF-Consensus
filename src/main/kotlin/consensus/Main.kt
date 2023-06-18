package consensus

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.Patterns
import akka.util.Timeout
import consensus.messages.AnswerMessage
import consensus.messages.LaunchLeaderMessage
import scala.concurrent.Await
import java.time.Duration
import java.time.LocalTime.now
import kotlin.random.Random

fun main() {
    for (processCount in listOf(1000, 1500, 2000, 2500, 3000)) {
        for (leaderElectionTime in listOf(500L, 1000L, 1500L)) {
            var res = consensus(processCount, 0.0, leaderElectionTime, 15)
            println("Processes: $processCount, Election timeout: $leaderElectionTime, Consensus latency: $res ms")
        }
    }

    for (processCount in listOf(2000)) {
        for (chanceToCrash in listOf(0.0, 0.1, 0.3, 0.5, 0.7, 1.0)) {
            val res = consensus(processCount, chanceToCrash, 500L, 15)
            println("Processes: $processCount, Chance to crash: $chanceToCrash, Consensus latency: $res ms")
        }
    }
}

private fun consensus(
    processCount: Int,
    chanceToCrash: Double,
    leaderElectionTimeoutMillis: Long,
    iterationCount: Int
): Long {
    val badProcessCount = processCount / 2 - 1

    val timeout: Timeout = Timeout.create(Duration.ofSeconds(150))

    var sumDecideTime: Long = 0

    (0 until iterationCount).forEach { _ ->
        val system = ActorSystem.create("search")
        val actors = mutableListOf<ActorRef>()

        val auxActor = system.actorOf(Props.create(AuxActor::class.java, processCount))

        (0 until badProcessCount).forEach {
            val actor =
                system.actorOf(Props.create(Process::class.java, it, processCount, chanceToCrash, actors, auxActor))
            actors.add(actor)
        }
        (badProcessCount until processCount).forEach {
            val actor = system.actorOf(Props.create(Process::class.java, it, processCount, 0.0, actors, auxActor))
            actors.add(actor)
        }

        var lastElection = now()

        var leaderId = Random.nextInt(badProcessCount, processCount)
        var leader = actors[leaderId]

        while (true) {
            val nowTime = now()

            if (Duration.between(lastElection, nowTime).toMillis() > leaderElectionTimeoutMillis) {
                lastElection = nowTime
                leaderId = Random.nextInt(badProcessCount, processCount)
                leader = actors[leaderId]
            }

            val message =
                Await.result(
                    Patterns.ask(auxActor, LaunchLeaderMessage(leader), timeout),
                    timeout.duration()
                ) as AnswerMessage

            if (message.decision != null) {
                val timeToDecide = Duration.between(message.launchTime, message.decideTime).toMillis()
                sumDecideTime += timeToDecide

                break
            }

            Thread.sleep(50)
        }

        system.terminate()
    }

    return sumDecideTime / iterationCount
}