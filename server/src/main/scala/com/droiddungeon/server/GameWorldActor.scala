package com.droiddungeon.server

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

import com.droiddungeon.input.{InputFrame, MovementIntent, WeaponInput}
import com.droiddungeon.items.GroundItem

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

  private case class BlockState(materialId: String, hp: Float)

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
          // initialize processed tick to -1
          val newProcessed = processedTicks + (playerId -> -1L)
          loop.registerPlayer(playerId)
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
            snapshotForPlayer(
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
          removedPlayers.foreach(pid => loop.unregisterPlayer(pid))
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
            else diffEnemies(enemiesAll, prevEnemies)

          var nextBlockCache = blockCacheByPlayer
          var nextGroundCache = prevGroundByPlayer
          sessions.foreach { case (pid, ref) =>
            val baseCache =
              if (forceFull) Map.empty[(Int, Int), BlockState]
              else blockCacheByPlayer.getOrElse(pid, Map.empty)
            val (snap, updatedBlockCache, updatedGroundCache) =
              snapshotForPlayer(
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

  private def snapshotForPlayer(
      loop: ServerGameLoop,
      playerId: String,
      playerIds: Iterable[String],
      tick: Long,
      full: Boolean,
      blockCache: Map[(Int, Int), BlockState],
      prevGround: Map[Int, GroundItemSnapshot],
      processedTicks: Map[String, Long],
      weaponStatesThisTick: Map[String, WeaponStateSnapshot],
      enemiesToSend: Seq[EnemySnapshot],
      enemyRemovals: Seq[Int]
  ): (
      WorldSnapshot,
      Map[(Int, Int), BlockState],
      Map[Int, GroundItemSnapshot]
  ) = {
    val centerOpt = Option(
      loop.playerSnapshotFor(playerId, processedTicks.getOrElse(playerId, -1L))
    )
    val (centerX, centerY) =
      centerOpt.map(p => (p.gridX, p.gridY)).getOrElse((0, 0))

    val (blockChanges, updatedBlockCache) =
      collectBlockChanges(loop.grid(), centerX, centerY, blockCache, radius = 8)
    val groundAll = collectGroundItems(
      loop.getGroundItems().asScala.toSeq,
      centerX,
      centerY,
      radius = 20
    )
    val (groundToSend, groundRemovals) =
      if (full) (groundAll, Seq.empty[Int])
      else diffGround(groundAll, prevGround)
    val chunks =
      if (full) collectChunks(loop.grid(), centerX, centerY, chunkRadius = 2)
      else Seq.empty
    val miningStatesAll = playerIds.toSeq.flatMap { id =>
      Option(loop.getPlayerMiningTarget(id)).map(t =>
        MiningStateSnapshot(id, t.x(), t.y(), t.progress())
      )
    }

    val snap = WorldSnapshot(
      tick = tick,
      seed = loop.worldSeed(),
      version = "0.1",
      full = full,
      chunks = chunks,
      players = playerIds.toSeq.flatMap { id =>
        val last = processedTicks.getOrElse(id, -1L)
        Option(loop.playerSnapshotFor(id, last)).toSeq.map { p =>
          PlayerSnapshot(
            p.playerId,
            p.x,
            p.y,
            p.gridX,
            p.gridY,
            p.hp,
            p.lastProcessedTick
          )
        }
      },
      enemies = enemiesToSend,
      enemyRemovals = enemyRemovals,
      blockChanges = blockChanges,
      groundItems = groundToSend,
      groundItemRemovals = groundRemovals,
      weaponStates = playerIds.toSeq.map { id =>
        weaponStatesThisTick
          .getOrElse(id, WeaponStateSnapshot(id, false, 0f, 0f))
      },
      miningStates = miningStatesAll
    )
    val nextGroundMap = groundAll.map(g => g.id -> g).toMap
    (snap, updatedBlockCache, nextGroundMap)
  }

  private def collectBlockChanges(
      grid: com.droiddungeon.grid.Grid,
      centerX: Int,
      centerY: Int,
      previous: Map[(Int, Int), BlockState],
      radius: Int
  ): (Seq[BlockChange], Map[(Int, Int), BlockState]) = {
    val buffer = mutable.ArrayBuffer.empty[BlockChange]
    var updated = previous

    for {
      x <- (centerX - radius) to (centerX + radius)
      y <- (centerY - radius) to (centerY + radius)
    } {
      val material = grid.getBlockMaterial(x, y)
      val materialId = Option(material).map(_.name()).getOrElse("")
      val hp = grid.getBlockHealth(x, y)
      val key = (x, y)
      previous.get(key) match {
        case Some(state)
            if state.materialId == materialId && math.abs(
              state.hp - hp
            ) < 0.0001 =>
        // unchanged
        case _ =>
          buffer += BlockChange(x, y, materialId, hp)
      }
      updated = updated + (key -> BlockState(materialId, hp))
    }
    (buffer.toSeq, updated)
  }

  private def collectGroundItems(
      groundItems: Seq[GroundItem],
      centerX: Int,
      centerY: Int,
      radius: Int
  ): Seq[GroundItemSnapshot] = {
    val r2 = radius * radius
    groundItems
      .filter { g =>
        val dx = g.getGridX - centerX
        val dy = g.getGridY - centerY
        dx * dx + dy * dy <= r2
      }
      .map { g =>
        val stack = g.getStack
        GroundItemSnapshot(
          g.id(),
          g.getGridX,
          g.getGridY,
          stack.itemId(),
          stack.count(),
          stack.durability()
        )
      }
  }

  private def diffEnemies(
      current: Seq[EnemySnapshot],
      previous: Map[Int, EnemySnapshot]
  ): (Seq[EnemySnapshot], Seq[Int]) = {
    val currentMap = current.map(e => e.id -> e).toMap
    val changed =
      current.filter(e => previous.get(e.id).forall(prev => prev != e))
    val removed = previous.keySet.diff(currentMap.keySet).toSeq
    (changed, removed)
  }

  private def diffGround(
      current: Seq[GroundItemSnapshot],
      previous: Map[Int, GroundItemSnapshot]
  ): (Seq[GroundItemSnapshot], Seq[Int]) = {
    val currentMap = current.map(g => g.id -> g).toMap
    val changed =
      current.filter(g => previous.get(g.id).forall(prev => prev != g))
    val removed = previous.keySet.diff(currentMap.keySet).toSeq
    (changed, removed)
  }

  private def collectChunks(
      grid: com.droiddungeon.grid.Grid,
      centerX: Int,
      centerY: Int,
      chunkRadius: Int
  ): Seq[ChunkSnapshot] = {
    val chunkSize = grid.getChunkSize()
    val pcx = Math.floorDiv(centerX, chunkSize)
    val pcy = Math.floorDiv(centerY, chunkSize)
    val chunks = mutable.ArrayBuffer.empty[ChunkSnapshot]
    for {
      cx <- (pcx - chunkRadius) to (pcx + chunkRadius)
      cy <- (pcy - chunkRadius) to (pcy + chunkRadius)
    } {
      val blocks = mutable.ArrayBuffer.empty[BlockChange]
      val originX = cx * chunkSize
      val originY = cy * chunkSize
      for {
        lx <- 0 until chunkSize
        ly <- 0 until chunkSize
      } {
        val x = originX + lx
        val y = originY + ly
        val material = grid.getBlockMaterial(x, y)
        val materialId = Option(material).map(_.name()).getOrElse("")
        val hp = grid.getBlockHealth(x, y)
        blocks += BlockChange(x, y, materialId, hp)
      }
      chunks += ChunkSnapshot(cx, cy, blocks.toSeq)
    }
    chunks.toSeq
  }
}
