package com.droiddungeon.server

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.TimerScheduler

import scala.concurrent.duration._

import com.droiddungeon.input.{InputFrame, MovementIntent, WeaponInput}
import com.droiddungeon.server.JsonProtocol.given

object GameWorldActor {
  sealed trait Command
  final case class RegisterSession(playerId: String, ref: ActorRef[WorldSnapshot]) extends Command
  final case class UnregisterSession(ref: ActorRef[WorldSnapshot]) extends Command
  final case class ApplyInput(input: ClientInput) extends Command
  private case object Tick extends Command

  def apply(loop: ServerGameLoop): Behavior[Command] =
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(Tick, 16.millis)
      active(loop, Map.empty, Map.empty, 0L)
    }

  private def active(
      loop: ServerGameLoop,
      sessions: Map[String, ActorRef[WorldSnapshot]],
      pendingInputs: Map[String, ClientInput],
      tick: Long
  ): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case RegisterSession(playerId, ref) =>
          val newSessions = sessions + (playerId -> ref)
          ctx.log.info("Session registered {} as player {}", ref, playerId)
          ref ! snapshot(loop, newSessions.keySet, tick)
          active(loop, newSessions, pendingInputs, tick)

        case UnregisterSession(ref) =>
          val filtered = sessions.filterNot { case (_, r) => r == ref }
          ctx.log.info("Session unregistered {}", ref)
          val filteredInputs = pendingInputs.filter { case (pid, _) => filtered.contains(pid) }
          active(loop, filtered, filteredInputs, tick)

        case ApplyInput(input) =>
          active(loop, sessions, pendingInputs + (input.playerId -> input), tick)

        case Tick =>
          pendingInputs.values.foreach { in =>
            val frame = toInputFrame(in)
            loop.tick(frame, 0.016f)
          }
          val snap = snapshot(loop, sessions.keySet, tick + 1)
          sessions.values.foreach(_ ! snap)
          active(loop, sessions, Map.empty, tick + 1)
      }
    }

  private def toInputFrame(in: ClientInput): InputFrame = {
    val m = in.movement
    val w = in.weapon
    val movement = new MovementIntent(
      m.leftHeld,
      m.rightHeld,
      m.upHeld,
      m.downHeld,
      m.leftJustPressed,
      m.rightJustPressed,
      m.upJustPressed,
      m.downJustPressed
    )
    val weapon = new WeaponInput(w.attackJustPressed, w.attackHeld, w.aimWorldX, w.aimWorldY)
    InputFrame.serverFrame(movement, weapon, in.drop, in.pickUp, in.mine)
  }

  private def snapshot(loop: ServerGameLoop, playerIds: Iterable[String], tick: Long): WorldSnapshot = {
    val ctx = loop.context()
    WorldSnapshot(
      tick,
      playerIds.toSeq.map { id =>
        PlayerSnapshot(
          id,
          ctx.player().getRenderX,
          ctx.player().getRenderY,
          ctx.player().getGridX,
          ctx.player().getGridY,
          ctx.playerStats().getHealth()
        )
      }
    )
  }
}
