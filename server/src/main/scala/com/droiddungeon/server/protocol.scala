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
final case class EnemySnapshot(id: Int, enemyType: String, x: Float, y: Float, gridX: Int, gridY: Int, hp: Float)
final case class BlockChange(x: Int, y: Int, materialId: String, blockHp: Float)
final case class GroundItemSnapshot(id: Int, x: Int, y: Int, itemId: String, count: Int, durability: Int)
final case class WeaponStateSnapshot(playerId: String, swinging: Boolean, swingProgress: Float, aimAngleRad: Float)
final case class MiningStateSnapshot(playerId: String, targetX: Int, targetY: Int, progress: Float)
final case class ChunkSnapshot(chunkX: Int, chunkY: Int, blocks: Seq[BlockChange])
final case class WorldSnapshot(
    tick: Long,
    seed: Long,
    version: String,
    full: Boolean,
    chunks: Seq[ChunkSnapshot],
    players: Seq[PlayerSnapshot],
    enemies: Seq[EnemySnapshot],
    enemyRemovals: Seq[Int],
    blockChanges: Seq[BlockChange],
    groundItems: Seq[GroundItemSnapshot],
    groundItemRemovals: Seq[Int],
    weaponStates: Seq[WeaponStateSnapshot],
    miningStates: Seq[MiningStateSnapshot]
)
final case class Welcome(playerId: String)

object JsonProtocol extends DefaultJsonProtocol {
  given movementFmt: RootJsonFormat[MovementIntentDto] = jsonFormat8(MovementIntentDto.apply)
  given weaponFmt: RootJsonFormat[WeaponInputDto]     = jsonFormat4(WeaponInputDto.apply)
  given clientFmt: RootJsonFormat[ClientInput]        = jsonFormat7(ClientInput.apply)
  given playerFmt: RootJsonFormat[PlayerSnapshot]     = jsonFormat6(PlayerSnapshot.apply)
  given enemyFmt: RootJsonFormat[EnemySnapshot]       = jsonFormat7(EnemySnapshot.apply)
  given blockFmt: RootJsonFormat[BlockChange]         = jsonFormat4(BlockChange.apply)
  given groundFmt: RootJsonFormat[GroundItemSnapshot] = jsonFormat6(GroundItemSnapshot.apply)
  given weaponStateFmt: RootJsonFormat[WeaponStateSnapshot] = jsonFormat4(WeaponStateSnapshot.apply)
  given miningStateFmt: RootJsonFormat[MiningStateSnapshot] = jsonFormat4(MiningStateSnapshot.apply)
  given chunkFmt: RootJsonFormat[ChunkSnapshot]       = jsonFormat3(ChunkSnapshot.apply)
  given worldFmt: RootJsonFormat[WorldSnapshot]       = jsonFormat13(WorldSnapshot.apply)
  given welcomeFmt: RootJsonFormat[Welcome]           = jsonFormat1(Welcome.apply)
}
