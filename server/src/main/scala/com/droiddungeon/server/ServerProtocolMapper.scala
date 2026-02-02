package com.droiddungeon.server

import com.droiddungeon.net.BinaryProtocol
import com.droiddungeon.net.dto.{
  ClientInputDto,
  WorldSnapshotDto,
  WelcomeDto,
  ChunkSnapshotDto,
  BlockChangeDto,
  PlayerSnapshotDto,
  EnemySnapshotDto,
  GroundItemSnapshotDto,
  WeaponStateSnapshotDto,
  MiningStateSnapshotDto
}
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.ByteBuffer
import org.apache.pekko.http.scaladsl.model.ws.BinaryMessage
import org.apache.pekko.util.ByteString

object ServerProtocolMapper:
  def deserializeInput(cbor: ObjectMapper, bytes: Array[Byte]): ClientInput = {
    val buffer = ByteBuffer.wrap(bytes)
    val header = BinaryProtocol.readHeader(buffer)
    if (header.version() != BinaryProtocol.VERSION_1)
      throw new IllegalArgumentException(s"Unsupported protocol version: ${header.version()}")
    if (header.`type`() != BinaryProtocol.TYPE_INPUT)
      throw new IllegalArgumentException(s"Unexpected message type: ${header.`type`()}")
    val payload = new Array[Byte](buffer.remaining())
    buffer.get(payload)
    val dto = cbor.readValue(payload, classOf[ClientInputDto])
    val m = dto.movement()
    val w = dto.weapon()
    val movement = MovementIntentDto(
      m.leftHeld(),
      m.rightHeld(),
      m.upHeld(),
      m.downHeld(),
      m.leftJustPressed(),
      m.rightJustPressed(),
      m.upJustPressed(),
      m.downJustPressed()
    )
    val weapon = WeaponInputDto(w.attackJustPressed(), w.attackHeld(), w.aimWorldX(), w.aimWorldY())
    ClientInput(dto.tick(), dto.playerId(), movement, weapon, dto.drop(), dto.pickUp(), dto.mine())
  }

  def toWorldSnapshotDto(snap: WorldSnapshot): WorldSnapshotDto = {
    val chunks = snap.chunks.map { c =>
      new ChunkSnapshotDto(
        c.chunkX,
        c.chunkY,
        c.blocks.map(b => new BlockChangeDto(b.x, b.y, b.materialId, b.blockHp)).toArray
      )
    }.toArray
    val players = snap.players.map { p =>
      new PlayerSnapshotDto(p.playerId, p.x, p.y, p.gridX, p.gridY, p.hp, p.lastProcessedTick)
    }.toArray
    val enemies = snap.enemies.map { e =>
      new EnemySnapshotDto(e.id, e.enemyType, e.x, e.y, e.gridX, e.gridY, e.hp)
    }.toArray
    val blockChanges = snap.blockChanges.map { b =>
      new BlockChangeDto(b.x, b.y, b.materialId, b.blockHp)
    }.toArray
    val groundItems = snap.groundItems.map { g =>
      new GroundItemSnapshotDto(g.id, g.x, g.y, g.itemId, g.count, g.durability)
    }.toArray
    val weaponStates = snap.weaponStates.map { w =>
      new WeaponStateSnapshotDto(w.playerId, w.swinging, w.swingProgress, w.aimAngleRad)
    }.toArray
    val miningStates = snap.miningStates.map { m =>
      new MiningStateSnapshotDto(m.playerId, m.targetX, m.targetY, m.progress)
    }.toArray

    new WorldSnapshotDto(
      snap.tick,
      snap.seed,
      snap.version,
      snap.full,
      chunks,
      null,
      players,
      enemies,
      snap.enemyRemovals.toArray,
      blockChanges,
      groundItems,
      snap.groundItemRemovals.toArray,
      weaponStates,
      miningStates
    )
  }

  def encodeSnapshotMessage(cbor: ObjectMapper, snap: WorldSnapshot): BinaryMessage = {
    val dto = toWorldSnapshotDto(snap)
    val payload = cbor.writeValueAsBytes(dto)
    val framed = BinaryProtocol.wrap(BinaryProtocol.TYPE_SNAPSHOT, payload)
    BinaryMessage(ByteString(framed))
  }

  def encodeWelcomeMessage(cbor: ObjectMapper, playerId: String): BinaryMessage = {
    val dto = new WelcomeDto(playerId, null, null)
    val payload = cbor.writeValueAsBytes(dto)
    val framed = BinaryProtocol.wrap(BinaryProtocol.TYPE_WELCOME, payload)
    BinaryMessage(ByteString(framed))
  }
