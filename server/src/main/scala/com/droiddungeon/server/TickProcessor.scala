package com.droiddungeon.server

import com.droiddungeon.input.{InputFrame, MovementIntent, WeaponInput}

import scala.jdk.CollectionConverters.*

final case class TickResult(
    tick: Long,
    processedTicks: Map[String, Long],
    weaponStates: Map[String, WeaponStateSnapshot],
    enemies: Seq[EnemySnapshot]
)

final case class TickProcessor(
    pendingInputs: Map[String, ClientInput],
    processedTicks: Map[String, Long],
    tick: Long
) {
  def registerPlayer(loop: ServerGameLoop, playerId: String): (TickProcessor, Long) = {
    val restoredTick = loop.registerPlayer(playerId)
    (copy(processedTicks = processedTicks + (playerId -> restoredTick)), restoredTick)
  }

  def unregisterPlayer(loop: ServerGameLoop, playerId: String): TickProcessor = {
    loop.savePlayerState(playerId, processedTicks.getOrElse(playerId, -1L))
    loop.unregisterPlayer(playerId)
    copy(
      pendingInputs = pendingInputs - playerId,
      processedTicks = processedTicks - playerId
    )
  }

  def enqueueInput(input: ClientInput): TickProcessor =
    copy(pendingInputs = pendingInputs + (input.playerId -> input))

  def processTick(loop: ServerGameLoop): (TickProcessor, TickResult) = {
    var newProcessed = processedTicks
    var weaponStatesThisTick = Map.empty[String, WeaponStateSnapshot]

    pendingInputs.foreach { case (pid, in) =>
      val frame = toInputFrame(in)
      val res = loop.tickForPlayer(pid, frame, 0.05f)
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

    loop.updateGlobal(0.05f)

    val nextTick = tick + 1
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

    val result = TickResult(nextTick, newProcessed, weaponStatesThisTick, enemiesAll)
    (
      copy(
        pendingInputs = Map.empty,
        processedTicks = newProcessed,
        tick = nextTick
      ),
      result
    )
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

object TickProcessor {
  val empty: TickProcessor = TickProcessor(Map.empty, Map.empty, 0L)
}
