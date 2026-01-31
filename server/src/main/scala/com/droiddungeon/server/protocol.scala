package com.droiddungeon.server

import spray.json.{DefaultJsonProtocol, RootJsonFormat}

final case class MovementIntentDto(
    leftHeld: Boolean,
    rightHeld: Boolean,
    upHeld: Boolean,
    downHeld: Boolean,
    leftJustPressed: Boolean,
    rightJustPressed: Boolean,
    upJustPressed: Boolean,
    downJustPressed: Boolean
)

final case class WeaponInputDto(
    attackJustPressed: Boolean,
    attackHeld: Boolean,
    aimWorldX: Float,
    aimWorldY: Float
)

final case class ClientInput(
    tick: Long,
    playerId: String,
    movement: MovementIntentDto,
    weapon: WeaponInputDto,
    drop: Boolean,
    pickUp: Boolean,
    mine: Boolean
)

final case class PlayerSnapshot(playerId: String, x: Float, y: Float, gridX: Int, gridY: Int, hp: Float)
final case class WorldSnapshot(tick: Long, players: Seq[PlayerSnapshot])
final case class Welcome(playerId: String)

object JsonProtocol extends DefaultJsonProtocol {
  given movementFmt: RootJsonFormat[MovementIntentDto] = jsonFormat8(MovementIntentDto.apply)
  given weaponFmt: RootJsonFormat[WeaponInputDto]     = jsonFormat4(WeaponInputDto.apply)
  given clientFmt: RootJsonFormat[ClientInput]        = jsonFormat7(ClientInput.apply)
  given playerFmt: RootJsonFormat[PlayerSnapshot]     = jsonFormat6(PlayerSnapshot.apply)
  given worldFmt: RootJsonFormat[WorldSnapshot]       = jsonFormat2(WorldSnapshot.apply)
  given welcomeFmt: RootJsonFormat[Welcome]           = jsonFormat1(Welcome.apply)
}
