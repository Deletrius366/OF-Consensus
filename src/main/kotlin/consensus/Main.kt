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
    consensus(1000, 0.0, 100, 10)
}

private fun consensus (processCount: Int, chanceToCrash: Double, leaderElectionTimeoutMillis: Long, iterationCount: Int) : Long {
    val badProcessCount = processCount / 2 - 1

    val timeout: Timeout = Timeout.create(Duration.ofSeconds(150))

    var sumDecideTime : Long = 0

    (0 until iterationCount).forEach { _ ->
        val system = ActorSystem.create("search")
        val actors = mutableListOf<ActorRef>()

        val auxActor = system.actorOf(Props.create(AuxActor::class.java, actors))

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

        var leaderId = Random.nextInt(0, processCount)
        var leader = actors[leaderId]

        while (true) {
            val nowTime = now()
//            println(Duration.between(lastElection, nowTime).toMillis())
            if (Duration.between(lastElection, nowTime).toMillis() > leaderElectionTimeoutMillis) {
                lastElection = nowTime
                leaderId = Random.nextInt(0, processCount)
                leader = actors[Random.nextInt(0, processCount)]

                // println("Process $leaderId was aborted or a new leader now")
            }

//            Patterns.ask(leader, LaunchMessage(Random.nextInt(0, 2)), timeout)
            val message =
                Await.result(
                    Patterns.ask(auxActor, LaunchLeaderMessage(leader), timeout),
                    timeout.duration()
                ) as AnswerMessage

            if (message.decision != null) {
                val timeToDecide = Duration.between(message.launchTime, message.decideTime).toMillis()
                sumDecideTime += timeToDecide

                // println("Time to decide: $timeToDecide ms, Decision: ${message.decision}")
                println()
                break
            }

            Thread.sleep(50)
        }
    }

    return sumDecideTime / iterationCount
}