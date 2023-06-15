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
    for (processCount in listOf(2000)) {
        for (leaderElectionTime in listOf(500L)) {
            var res = consensus(processCount, 0.0, leaderElectionTime, 15)
            println("Processes: $processCount, Election timeout: $leaderElectionTime, Consensus latency: $res ms")
//            val res = consensus(processCount, 1.0, leaderElectionTime, 15)
//            println("Processes: $processCount, Election timeout: $leaderElectionTime, Consensus latency: $res ms")
        }
    }
//    for (processCount in listOf(500, 1000, 2000)) {
//        for (leaderElectionTime in listOf(500L, 1000L, 1500L)) {
//            val res = consensus(processCount, 0.0, leaderElectionTime, 15)
//            println("Processes: $processCount, Election timeout: $leaderElectionTime, Consensus latency: $res ms")
//        }
//    }
}

private fun consensus(
    processCount: Int,
    chanceToCrash: Double,
    leaderElectionTimeoutMillis: Long,
    iterationCount: Int
): Long {
    val seed = processCount + leaderElectionTimeoutMillis + iterationCount

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

        var leaderId = Random(seed).nextInt(badProcessCount, processCount)
        var leader = actors[leaderId]

        while (true) {
            val nowTime = now()
//            println(Duration.between(lastElection, nowTime).toMillis())
            if (Duration.between(lastElection, nowTime).toMillis() > leaderElectionTimeoutMillis) {
                lastElection = nowTime
                leaderId = Random(seed).nextInt(badProcessCount, processCount)
                leader = actors[leaderId]

                // println("Process $leaderId was aborted or a new leader now")
            }

//            Patterns.ask(leader, LaunchMessage(Random(seed).nextInt(0, 2)), timeout)
            val message =
                Await.result(
                    Patterns.ask(auxActor, LaunchLeaderMessage(leader), timeout),
                    timeout.duration()
                ) as AnswerMessage

            if (message.decision != null) {
                val timeToDecide = Duration.between(message.launchTime, message.decideTime).toMillis()
                sumDecideTime += timeToDecide

                // println("Time to decide: $timeToDecide ms, Decision: ${message.decision}")
//                println()
                break
            }

            Thread.sleep(50)
        }

        system.terminate()
//        actors.forEach {
//            system.stop(it)
//        }
    }

    return sumDecideTime / iterationCount
}