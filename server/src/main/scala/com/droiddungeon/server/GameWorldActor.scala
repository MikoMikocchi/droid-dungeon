package com.droiddungeon.server

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.TimerScheduler

import scala.concurrent.duration._
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

import com.droiddungeon.input.{InputFrame, MovementIntent, WeaponInput}
import com.droiddungeon.runtime.GameContext
import com.droiddungeon.items.GroundItem
import com.droiddungeon.server.JsonProtocol.given

object GameWorldActor {
  sealed trait Command
  final case class RegisterSession(playerId: String, ref: ActorRef[WorldSnapshot]) extends Command
  final case class UnregisterSession(ref: ActorRef[WorldSnapshot]) extends Command
  final case class ApplyInput(input: ClientInput) extends Command
  private case object Tick extends Command
  private val KeyframeEvery = 20 // every ~1s at 50ms tick

  private case class BlockState(materialId: String, hp: Float)

  def apply(loop: ServerGameLoop): Behavior[Command] =
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(Tick, 50.millis)
      active(loop, Map.empty, Map.empty, 0L, Map.empty, Map.empty, Map.empty)
    }

  private def active(
      loop: ServerGameLoop,
      sessions: Map[String, ActorRef[WorldSnapshot]],
      pendingInputs: Map[String, ClientInput],
      tick: Long,
      blockCache: Map[(Int, Int), BlockState],
      prevEnemies: Map[Int, EnemySnapshot],
      prevGround: Map[Int, GroundItemSnapshot]
  ): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case RegisterSession(playerId, ref) =>
          val newSessions = sessions + (playerId -> ref)
          ctx.log.info("Session registered {} as player {}", ref, playerId)
          ref ! snapshot(loop, newSessions.keySet, tick, Seq.empty, full = true, Map.empty, Map.empty)
          active(loop, newSessions, pendingInputs, tick, blockCache, prevEnemies, prevGround)

        case UnregisterSession(ref) =>
          val filtered = sessions.filterNot { case (_, r) => r == ref }
          ctx.log.info("Session unregistered {}", ref)
          val filteredInputs = pendingInputs.filter { case (pid, _) => filtered.contains(pid) }
          active(loop, filtered, filteredInputs, tick, blockCache, prevEnemies, prevGround)

        case ApplyInput(input) =>
          active(loop, sessions, pendingInputs + (input.playerId -> input), tick, blockCache, prevEnemies, prevGround)

        case Tick =>
          pendingInputs.values.foreach { in =>
            val frame = toInputFrame(in)
            loop.tick(frame, 0.05f)
          }
          val nextTick = tick + 1
          val forceFull = nextTick % KeyframeEvery == 0
          val baseCache = if (forceFull) Map.empty[(Int, Int), BlockState] else blockCache
          val (blockChanges, updatedCache) =
            collectBlockChanges(loop.context(), baseCache, radius = 8)
          val snap = snapshot(loop, sessions.keySet, nextTick, blockChanges, full = forceFull, prevEnemies, prevGround)
          sessions.values.foreach(_ ! snap)
          val nextEnemies = snap.enemies.map(e => e.id -> e).toMap
          val nextGround   = snap.groundItems.map(g => g.id -> g).toMap
          active(loop, sessions, Map.empty, nextTick, updatedCache, nextEnemies, nextGround)
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
      blockChanges: Seq[BlockChange],
      full: Boolean,
      prevEnemies: Map[Int, EnemySnapshot],
      prevGround: Map[Int, GroundItemSnapshot]
  ): WorldSnapshot = {
    val ctx = loop.context()
    val enemiesAll = ctx.enemySystem().getEnemies().asScala.toSeq.map { e =>
      EnemySnapshot(e.id(), e.getType.toString, e.getRenderX, e.getRenderY, e.getGridX, e.getGridY, e.getHealth)
    }
    val groundAll = collectGroundItems(ctx, ctx.player().getGridX, ctx.player().getGridY, radius = 20)
    val (enemiesToSend, enemyRemovals) =
      if (full) (enemiesAll, Seq.empty[Int])
      else diffEnemies(enemiesAll, prevEnemies)
    val (groundToSend, groundRemovals) =
      if (full) (groundAll, Seq.empty[Int])
      else diffGround(groundAll, prevGround)
    val chunks = if (full) collectChunks(ctx, chunkRadius = 2) else Seq.empty
    val weaponState = ctx.weaponSystem().getState()
    val miningTargetOpt = Option(ctx.miningSystem().getTarget())
    WorldSnapshot(
      tick = tick,
      seed = loop.worldSeed(),
      version = "0.1",
      full = full,
      chunks = chunks,
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
      enemies = enemiesToSend,
      enemyRemovals = enemyRemovals,
      blockChanges = blockChanges,
      groundItems = groundToSend,
      groundItemRemovals = groundRemovals,
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

  private def collectGroundItems(ctx: GameContext, centerX: Int, centerY: Int, radius: Int): Seq[GroundItemSnapshot] = {
    val r2 = radius * radius
      ctx.inventorySystem().getGroundItems().asScala.toSeq
      .filter { g =>
        val dx = g.getGridX - centerX
        val dy = g.getGridY - centerY
        dx * dx + dy * dy <= r2
      }
      .map { g =>
        val stack = g.getStack
        GroundItemSnapshot(g.id(), g.getGridX, g.getGridY, stack.itemId(), stack.count(), stack.durability())
      }
  }

  private def diffEnemies(current: Seq[EnemySnapshot], previous: Map[Int, EnemySnapshot]): (Seq[EnemySnapshot], Seq[Int]) = {
    val currentMap = current.map(e => e.id -> e).toMap
    val changed = current.filter(e => previous.get(e.id).forall(prev => prev != e))
    val removed = previous.keySet.diff(currentMap.keySet).toSeq
    (changed, removed)
  }

  private def diffGround(current: Seq[GroundItemSnapshot], previous: Map[Int, GroundItemSnapshot]): (Seq[GroundItemSnapshot], Seq[Int]) = {
    val currentMap = current.map(g => g.id -> g).toMap
    val changed = current.filter(g => previous.get(g.id).forall(prev => prev != g))
    val removed = previous.keySet.diff(currentMap.keySet).toSeq
    (changed, removed)
  }

  private def collectChunks(ctx: GameContext, chunkRadius: Int): Seq[ChunkSnapshot] = {
    val grid = ctx.grid()
    val chunkSize = grid.getChunkSize()
    val pcx = Math.floorDiv(ctx.player().getGridX, chunkSize)
    val pcy = Math.floorDiv(ctx.player().getGridY, chunkSize)
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
