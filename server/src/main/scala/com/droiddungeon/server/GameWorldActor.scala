package com.droiddungeon.server

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.TimerScheduler

import scala.concurrent.duration._
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

import com.droiddungeon.input.{InputFrame, MovementIntent, WeaponInput}
import com.droiddungeon.runtime.GameContext
import com.droiddungeon.server.JsonProtocol.given

object GameWorldActor {
  sealed trait Command
  final case class RegisterSession(playerId: String, ref: ActorRef[WorldSnapshot]) extends Command
  final case class UnregisterSession(ref: ActorRef[WorldSnapshot]) extends Command
  final case class ApplyInput(input: ClientInput) extends Command
  private case object Tick extends Command

  private case class BlockState(materialId: String, hp: Float)

  def apply(loop: ServerGameLoop): Behavior[Command] =
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(Tick, 16.millis)
      active(loop, Map.empty, Map.empty, 0L, Map.empty)
    }

  private def active(
      loop: ServerGameLoop,
      sessions: Map[String, ActorRef[WorldSnapshot]],
      pendingInputs: Map[String, ClientInput],
      tick: Long,
      blockCache: Map[(Int, Int), BlockState]
  ): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case RegisterSession(playerId, ref) =>
          val newSessions = sessions + (playerId -> ref)
          ctx.log.info("Session registered {} as player {}", ref, playerId)
          ref ! snapshot(loop, newSessions.keySet, tick, Seq.empty)
          active(loop, newSessions, pendingInputs, tick, blockCache)

        case UnregisterSession(ref) =>
          val filtered = sessions.filterNot { case (_, r) => r == ref }
          ctx.log.info("Session unregistered {}", ref)
          val filteredInputs = pendingInputs.filter { case (pid, _) => filtered.contains(pid) }
          active(loop, filtered, filteredInputs, tick, blockCache)

        case ApplyInput(input) =>
          active(loop, sessions, pendingInputs + (input.playerId -> input), tick, blockCache)

        case Tick =>
          pendingInputs.values.foreach { in =>
            val frame = toInputFrame(in)
            loop.tick(frame, 0.016f)
          }
          val (blockChanges, updatedCache) =
            collectBlockChanges(loop.context(), blockCache, radius = 12)
          val snap = snapshot(loop, sessions.keySet, tick + 1, blockChanges)
          sessions.values.foreach(_ ! snap)
          active(loop, sessions, Map.empty, tick + 1, updatedCache)
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

  private def snapshot(
      loop: ServerGameLoop,
      playerIds: Iterable[String],
      tick: Long,
      blockChanges: Seq[BlockChange]
  ): WorldSnapshot = {
    val ctx = loop.context()
    val enemies = ctx.enemySystem().getEnemies().asScala.toSeq.map { e =>
      EnemySnapshot(e.id(), e.getType.toString, e.getRenderX, e.getRenderY, e.getGridX, e.getGridY, e.getHealth)
    }
    val weaponState = ctx.weaponSystem().getState()
    val miningTargetOpt = Option(ctx.miningSystem().getTarget())
    WorldSnapshot(
      tick = tick,
      seed = loop.worldSeed(),
      version = "0.1",
      players = playerIds.toSeq.map { id =>
        PlayerSnapshot(
          id,
          ctx.player().getRenderX,
          ctx.player().getRenderY,
          ctx.player().getGridX,
          ctx.player().getGridY,
          ctx.playerStats().getHealth()
        )
      },
      enemies = enemies,
      blockChanges = blockChanges,
      groundItems = Seq.empty,
      weaponStates = playerIds.toSeq.map { id =>
        WeaponStateSnapshot(id, weaponState.swinging(), weaponState.swingProgress(), weaponState.aimAngleRad())
      },
      miningStates = miningTargetOpt.toSeq.map(t => MiningStateSnapshot(playerIds.headOption.getOrElse(""), t.x(), t.y(), t.progress()))
    )
  }

  private def collectBlockChanges(
      ctx: GameContext,
      previous: Map[(Int, Int), BlockState],
      radius: Int
  ): (Seq[BlockChange], Map[(Int, Int), BlockState]) = {
    val player = ctx.player()
    val centerX = player.getGridX
    val centerY = player.getGridY
    val buffer = mutable.ArrayBuffer.empty[BlockChange]
    var updated = previous

    for {
      x <- (centerX - radius) to (centerX + radius)
      y <- (centerY - radius) to (centerY + radius)
    } {
      val material = ctx.grid().getBlockMaterial(x, y)
      val materialId = Option(material).map(_.name()).getOrElse("")
      val hp = ctx.grid().getBlockHealth(x, y)
      val key = (x, y)
      previous.get(key) match {
        case Some(state) if state.materialId == materialId && math.abs(state.hp - hp) < 0.0001 =>
          // unchanged
        case _ =>
          buffer += BlockChange(x, y, materialId, hp)
      }
      updated = updated + (key -> BlockState(materialId, hp))
    }
    (buffer.toSeq, updated)
  }
}
