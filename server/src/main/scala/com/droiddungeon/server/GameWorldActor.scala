package com.droiddungeon.server

import com.droiddungeon.net.dto.{
  ClientInputDto,
  EnemySnapshotDto,
  WorldSnapshotDto
}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._
import scala.jdk.CollectionConverters.*

object GameWorldActor {
  sealed trait Command
  final case class RegisterSession(
      playerId: String,
      ref: ActorRef[WorldSnapshotDto]
  ) extends Command
  final case class UnregisterSession(ref: ActorRef[WorldSnapshotDto])
      extends Command
  final case class ApplyInput(input: ClientInputDto) extends Command
  final case class AdvanceGlobal(dt: Float) extends Command
  private case object Tick extends Command
  def apply(loop: ServerGameLoop): Behavior[Command] =
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(Tick, 50.millis)
      active(
        loop,
        SessionRegistry.empty,
        TickProcessor.empty,
        SnapshotService.empty
      )
    }

  private def active(
      loop: ServerGameLoop,
      sessions: SessionRegistry,
      ticks: TickProcessor,
      snapshots: SnapshotService
  ): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case RegisterSession(playerId, ref) =>
          val updatedSessions = sessions.register(playerId, ref)
          ctx.log.info("Session registered {} as player {}", ref, playerId)
          val (updatedTicks, _) = ticks.registerPlayer(loop, playerId)
          val enemiesAll =
            loop.enemySystem().getEnemies().asScala.toSeq.map { e =>
              new EnemySnapshotDto(
                e.id(),
                e.getType.toString,
                e.getRenderX,
                e.getRenderY,
                e.getGridX,
                e.getGridY,
                e.getHealth
              )
            }
          val (updatedSnapshots, snap) = snapshots.buildInitialSnapshot(
            loop,
            playerId,
            updatedSessions.sessions.keySet,
            updatedTicks.tick,
            updatedTicks.processedTicks,
            enemiesAll
          )
          ref ! snap
          active(loop, updatedSessions, updatedTicks, updatedSnapshots)

        case UnregisterSession(ref) =>
          val (updatedSessions, removedPlayers) = sessions.unregister(ref)
          ctx.log.info("Session unregistered {}", ref)
          val nextTicks = removedPlayers.foldLeft(ticks) { (acc, pid) =>
            acc.unregisterPlayer(loop, pid)
          }
          val nextSnapshots = snapshots.removePlayers(removedPlayers)
          active(loop, updatedSessions, nextTicks, nextSnapshots)

        case ApplyInput(input) =>
          if (sessions.contains(input.playerId())) {
            val nextTicks = ticks.enqueueInput(input)
            active(loop, sessions, nextTicks, snapshots)
          } else {
            Behaviors.same
          }

        case AdvanceGlobal(dt) =>
          loop.updateGlobal(dt)
          Behaviors.same

        case Tick =>
          val (nextTicks, tickResult) = ticks.processTick(loop)
          val (nextSnapshots, outgoing) = snapshots.buildSnapshots(
            loop,
            sessions.sessions,
            tickResult.tick,
            tickResult.processedTicks,
            tickResult.weaponStates,
            tickResult.enemies
          )
          outgoing.foreach { case (ref, snap) => ref ! snap }
          active(loop, sessions, nextTicks, nextSnapshots)
      }
    }

}
