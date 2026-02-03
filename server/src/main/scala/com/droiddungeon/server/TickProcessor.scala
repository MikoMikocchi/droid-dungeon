package com.droiddungeon.server

import com.droiddungeon.input.InputFrame
import com.droiddungeon.net.dto.{
  ClientInputDto,
  EnemySnapshotDto,
  WeaponStateSnapshotDto
}
import com.droiddungeon.net.mapper.InputDtoMapper

import scala.jdk.CollectionConverters.*

final case class TickResult(
    tick: Long,
    processedTicks: Map[String, Long],
    weaponStates: Map[String, WeaponStateSnapshotDto],
    enemies: Seq[EnemySnapshotDto]
)

final case class TickProcessor(
    pendingInputs: Map[String, ClientInputDto],
    processedTicks: Map[String, Long],
    tick: Long
) {
  def registerPlayer(
      loop: ServerGameLoop,
      playerId: String
  ): (TickProcessor, Long) = {
    val restoredTick = loop.registerPlayer(playerId)
    (
      copy(processedTicks = processedTicks + (playerId -> restoredTick)),
      restoredTick
    )
  }

  def unregisterPlayer(
      loop: ServerGameLoop,
      playerId: String
  ): TickProcessor = {
    loop.savePlayerState(playerId, processedTicks.getOrElse(playerId, -1L))
    loop.unregisterPlayer(playerId)
    copy(
      pendingInputs = pendingInputs - playerId,
      processedTicks = processedTicks - playerId
    )
  }

  def enqueueInput(input: ClientInputDto): TickProcessor =
    copy(pendingInputs = pendingInputs + (input.playerId() -> input))

  def processTick(loop: ServerGameLoop): (TickProcessor, TickResult) = {
    var newProcessed = processedTicks
    var weaponStatesThisTick = Map.empty[String, WeaponStateSnapshotDto]

    pendingInputs.foreach { case (pid, in) =>
      val frame = toInputFrame(in)
      val res = loop.tickForPlayer(pid, frame, 0.05f)
      newProcessed = newProcessed + (in.playerId() -> in.tick())
      if (res != null && res.weaponState() != null) {
        val w = res.weaponState()
        weaponStatesThisTick =
          weaponStatesThisTick + (pid -> new WeaponStateSnapshotDto(
            pid,
            w.swinging(),
            w.swingProgress(),
            w.aimAngleRad()
          ))
      }
    }

    loop.updateGlobal(0.05f)

    val nextTick = tick + 1
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

    val result =
      TickResult(nextTick, newProcessed, weaponStatesThisTick, enemiesAll)
    (
      copy(
        pendingInputs = Map.empty,
        processedTicks = newProcessed,
        tick = nextTick
      ),
      result
    )
  }

  private def toInputFrame(in: ClientInputDto): InputFrame =
    InputDtoMapper.toInputFrame(in)
}

object TickProcessor {
  val empty: TickProcessor = TickProcessor(Map.empty, Map.empty, 0L)
}
