package com.droiddungeon.server

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._
import scala.jdk.CollectionConverters.*
import com.droiddungeon.server.WorldSnapshotBuilder.BlockState

import com.droiddungeon.input.{InputFrame, MovementIntent, WeaponInput}

object GameWorldActor {
  sealed trait Command
  final case class RegisterSession(
      playerId: String,
      ref: ActorRef[WorldSnapshot]
  ) extends Command
  final case class UnregisterSession(ref: ActorRef[WorldSnapshot])
      extends Command
  final case class ApplyInput(input: ClientInput) extends Command
  final case class AdvanceGlobal(dt: Float) extends Command
  private case object Tick extends Command
  private val KeyframeEvery = 20 // every ~1s at 50ms tick

  def apply(loop: ServerGameLoop): Behavior[Command] =
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(Tick, 50.millis)
      // processedTicks: last applied tick per player for acking inputs
      active(
        loop,
        Map.empty,
        Map.empty,
        0L,
        Map.empty,
        Map.empty,
        Map.empty,
        Map.empty
      )
    }

  private def active(
      loop: ServerGameLoop,
      sessions: Map[String, ActorRef[WorldSnapshot]],
      pendingInputs: Map[String, ClientInput],
      tick: Long,
      blockCacheByPlayer: Map[String, Map[(Int, Int), BlockState]],
      prevEnemies: Map[Int, EnemySnapshot],
      prevGroundByPlayer: Map[String, Map[Int, GroundItemSnapshot]],
      processedTicks: Map[String, Long]
  ): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case RegisterSession(playerId, ref) =>
          val newSessions = sessions + (playerId -> ref)
          ctx.log.info("Session registered {} as player {}", ref, playerId)
          val restoredTick = loop.registerPlayer(playerId)
          val newProcessed = processedTicks + (playerId -> restoredTick)
          val enemiesAll =
            loop.enemySystem().getEnemies().asScala.toSeq.map { e =>
              EnemySnapshot(
                e.id(),
                e.getType.toString,
                e.getRenderX,
                e.getRenderY,
                e.getGridX,
                e.getGridY,
                e.getHealth
              )
            }
          val (snap, updatedBlockCache, updatedGroundCache) =
            WorldSnapshotBuilder.snapshotForPlayer(
              loop,
              playerId,
              newSessions.keySet,
              tick,
              full = true,
              blockCacheByPlayer.getOrElse(playerId, Map.empty),
              prevGroundByPlayer.getOrElse(playerId, Map.empty),
              newProcessed,
              Map.empty,
              enemiesAll,
              Seq.empty
            )
          ref ! snap
          val nextBlockCache =
            blockCacheByPlayer + (playerId -> updatedBlockCache)
          val nextGroundCache =
            prevGroundByPlayer + (playerId -> updatedGroundCache)
          active(
            loop,
            newSessions,
            pendingInputs,
            tick,
            nextBlockCache,
            prevEnemies,
            nextGroundCache,
            newProcessed
          )

        case UnregisterSession(ref) =>
          val filtered = sessions.filterNot { case (_, r) => r == ref }
          ctx.log.info("Session unregistered {}", ref)
          val filteredInputs = pendingInputs.filter { case (pid, _) =>
            filtered.contains(pid)
          }
          val filteredProcessed = processedTicks.filter { case (pid, _) =>
            filtered.contains(pid)
          }
          val filteredBlockCache = blockCacheByPlayer.filter { case (pid, _) =>
            filtered.contains(pid)
          }
          val filteredGroundCache = prevGroundByPlayer.filter { case (pid, _) =>
            filtered.contains(pid)
          }
          // unregister player from loop
          val removedPlayers = sessions.keySet.diff(filtered.keySet)
          removedPlayers.foreach { pid =>
            loop.savePlayerState(pid, processedTicks.getOrElse(pid, -1L))
            loop.unregisterPlayer(pid)
          }
          active(
            loop,
            filtered,
            filteredInputs,
            tick,
            filteredBlockCache,
            prevEnemies,
            filteredGroundCache,
            filteredProcessed
          )

        case ApplyInput(input) =>
          if (sessions.contains(input.playerId))
            active(
              loop,
              sessions,
              pendingInputs + (input.playerId -> input),
              tick,
              blockCacheByPlayer,
              prevEnemies,
              prevGroundByPlayer,
              processedTicks
            )
          else
            Behaviors.same

        case AdvanceGlobal(dt) =>
          loop.updateGlobal(dt)
          active(
            loop,
            sessions,
            pendingInputs,
            tick,
            blockCacheByPlayer,
            prevEnemies,
            prevGroundByPlayer,
            processedTicks
          )

        case Tick =>
          var newProcessed = processedTicks
          var weaponStatesThisTick = Map.empty[String, WeaponStateSnapshot]
          // apply each player's latest input to their session
          pendingInputs.foreach { case (pid, in) =>
            val frame = toInputFrame(in)
            val res = loop.tickForPlayer(pid, frame, 0.05f)
            // record that we've processed this player's input tick
            newProcessed = newProcessed + (in.playerId -> in.tick)
            if (res != null && res.weaponState() != null) {
              val w = res.weaponState()
              weaponStatesThisTick =
                weaponStatesThisTick + (pid -> WeaponStateSnapshot(
                  pid,
                  w.swinging(),
                  w.swingProgress(),
                  w.aimAngleRad()
                ))
            }
          }

          // after applying player inputs, run global updates (AI, spawns)
          loop.updateGlobal(0.05f)

          val nextTick = tick + 1
          val forceFull = nextTick % KeyframeEvery == 0
          val enemiesAll =
            loop.enemySystem().getEnemies().asScala.toSeq.map { e =>
              EnemySnapshot(
                e.id(),
                e.getType.toString,
                e.getRenderX,
                e.getRenderY,
                e.getGridX,
                e.getGridY,
                e.getHealth
              )
            }
          val (enemiesToSend, enemyRemovals) =
            if (forceFull) (enemiesAll, Seq.empty[Int])
            else WorldSnapshotBuilder.diffEnemies(enemiesAll, prevEnemies)

          var nextBlockCache = blockCacheByPlayer
          var nextGroundCache = prevGroundByPlayer
          sessions.foreach { case (pid, ref) =>
            val baseCache =
              if (forceFull) Map.empty[(Int, Int), BlockState]
              else blockCacheByPlayer.getOrElse(pid, Map.empty)
            val (snap, updatedBlockCache, updatedGroundCache) =
              WorldSnapshotBuilder.snapshotForPlayer(
                loop,
                pid,
                sessions.keySet,
                nextTick,
                full = forceFull,
                baseCache,
                prevGroundByPlayer.getOrElse(pid, Map.empty),
                newProcessed,
                weaponStatesThisTick,
                enemiesToSend,
                enemyRemovals
              )
            ref ! snap
            nextBlockCache = nextBlockCache + (pid -> updatedBlockCache)
            nextGroundCache = nextGroundCache + (pid -> updatedGroundCache)
          }

          val nextEnemies = enemiesAll.map(e => e.id -> e).toMap
          active(
            loop,
            sessions,
            Map.empty,
            nextTick,
            nextBlockCache,
            nextEnemies,
            nextGroundCache,
            newProcessed
          )
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
    val weapon = new WeaponInput(
      w.attackJustPressed,
      w.attackHeld,
      w.aimWorldX,
      w.aimWorldY
    )
    InputFrame.serverFrame(movement, weapon, in.drop, in.pickUp, in.mine)
  }

}
