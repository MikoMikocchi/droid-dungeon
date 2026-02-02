package com.droiddungeon.server

import com.droiddungeon.items.GroundItem
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

object WorldSnapshotBuilder:
  final case class BlockState(materialId: String, hp: Float)

  def snapshotForPlayer(
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
  ): (WorldSnapshot, Map[(Int, Int), BlockState], Map[Int, GroundItemSnapshot]) = {
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

  def collectBlockChanges(
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

  def collectGroundItems(
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

  def diffEnemies(
      current: Seq[EnemySnapshot],
      previous: Map[Int, EnemySnapshot]
  ): (Seq[EnemySnapshot], Seq[Int]) = {
    val currentMap = current.map(e => e.id -> e).toMap
    val changed =
      current.filter(e => previous.get(e.id).forall(prev => prev != e))
    val removed = previous.keySet.diff(currentMap.keySet).toSeq
    (changed, removed)
  }

  def diffGround(
      current: Seq[GroundItemSnapshot],
      previous: Map[Int, GroundItemSnapshot]
  ): (Seq[GroundItemSnapshot], Seq[Int]) = {
    val currentMap = current.map(g => g.id -> g).toMap
    val changed =
      current.filter(g => previous.get(g.id).forall(prev => prev != g))
    val removed = previous.keySet.diff(currentMap.keySet).toSeq
    (changed, removed)
  }

  def collectChunks(
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
