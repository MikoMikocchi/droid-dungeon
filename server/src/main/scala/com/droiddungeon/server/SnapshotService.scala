package com.droiddungeon.server

import org.apache.pekko.actor.typed.ActorRef

import com.droiddungeon.server.WorldSnapshotBuilder.BlockState

final case class SnapshotService(
    blockCacheByPlayer: Map[String, Map[(Int, Int), BlockState]],
    groundCacheByPlayer: Map[String, Map[Int, GroundItemSnapshot]],
    prevEnemies: Map[Int, EnemySnapshot]
) {
  def removePlayers(playerIds: Set[String]): SnapshotService =
    copy(
      blockCacheByPlayer = blockCacheByPlayer -- playerIds,
      groundCacheByPlayer = groundCacheByPlayer -- playerIds
    )

  def buildInitialSnapshot(
      loop: ServerGameLoop,
      playerId: String,
      playerIds: Iterable[String],
      tick: Long,
      processedTicks: Map[String, Long],
      enemiesAll: Seq[EnemySnapshot]
  ): (SnapshotService, WorldSnapshot) = {
    val (snap, updatedBlockCache, updatedGroundCache) =
      WorldSnapshotBuilder.snapshotForPlayer(
        loop,
        playerId,
        playerIds,
        tick,
        full = true,
        blockCacheByPlayer.getOrElse(playerId, Map.empty),
        groundCacheByPlayer.getOrElse(playerId, Map.empty),
        processedTicks,
        Map.empty,
        enemiesAll,
        Seq.empty
      )
    val next = copy(
      blockCacheByPlayer = blockCacheByPlayer + (playerId -> updatedBlockCache),
      groundCacheByPlayer = groundCacheByPlayer + (playerId -> updatedGroundCache)
    )
    (next, snap)
  }

  def buildSnapshots(
      loop: ServerGameLoop,
      sessions: Map[String, ActorRef[WorldSnapshot]],
      tick: Long,
      processedTicks: Map[String, Long],
      weaponStatesThisTick: Map[String, WeaponStateSnapshot],
      enemiesAll: Seq[EnemySnapshot]
  ): (SnapshotService, Seq[(ActorRef[WorldSnapshot], WorldSnapshot)]) = {
    val forceFull = tick % SnapshotService.KeyframeEvery == 0
    val (enemiesToSend, enemyRemovals) =
      if (forceFull) (enemiesAll, Seq.empty[Int])
      else WorldSnapshotBuilder.diffEnemies(enemiesAll, prevEnemies)

    var nextBlockCache = blockCacheByPlayer
    var nextGroundCache = groundCacheByPlayer

    val outgoing = sessions.toSeq.map { case (pid, ref) =>
      val baseCache =
        if (forceFull) Map.empty[(Int, Int), BlockState]
        else blockCacheByPlayer.getOrElse(pid, Map.empty)
      val (snap, updatedBlockCache, updatedGroundCache) =
        WorldSnapshotBuilder.snapshotForPlayer(
          loop,
          pid,
          sessions.keySet,
          tick,
          full = forceFull,
          baseCache,
          groundCacheByPlayer.getOrElse(pid, Map.empty),
          processedTicks,
          weaponStatesThisTick,
          enemiesToSend,
          enemyRemovals
        )
      nextBlockCache = nextBlockCache + (pid -> updatedBlockCache)
      nextGroundCache = nextGroundCache + (pid -> updatedGroundCache)
      (ref, snap)
    }

    val nextEnemies = enemiesAll.map(e => e.id -> e).toMap
    (
      copy(
        blockCacheByPlayer = nextBlockCache,
        groundCacheByPlayer = nextGroundCache,
        prevEnemies = nextEnemies
      ),
      outgoing
    )
  }
}

object SnapshotService {
  val KeyframeEvery = 20 // every ~1s at 50ms tick
  val empty: SnapshotService = SnapshotService(Map.empty, Map.empty, Map.empty)
}
