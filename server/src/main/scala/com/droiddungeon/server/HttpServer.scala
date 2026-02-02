package com.droiddungeon.server

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.http.scaladsl.model.ws.{BinaryMessage, Message}
import org.apache.pekko.stream.scaladsl.{Sink, Source, Keep}
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.util.ByteString

import com.droiddungeon.config.GameConfig
import com.droiddungeon.items.ItemRegistry
import com.droiddungeon.server.ServerGameLoop
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
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import java.nio.ByteBuffer
import java.nio.file.{Files, Paths}
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success}
import scala.concurrent.duration.*
import scala.util.Try

object HttpServer:
  private def websocketFlow(world: org.apache.pekko.actor.typed.ActorRef[GameWorldActor.Command])(using system: ActorSystem[Nothing]): Flow[Message, Message, Any] =
    import system.executionContext
    val playerId = java.util.UUID.randomUUID().toString
    val cbor = new ObjectMapper(new CBORFactory()).findAndRegisterModules()

    val sink: Sink[Message, Any] = Flow[Message]
      .collect { case bm: BinaryMessage => bm }
      .mapAsync(1)(_.toStrict(2.seconds).map(_.data).recover { case ex =>
        system.log.warn("Failed to read streamed binary message, ignoring: {}", ex.getMessage)
        ByteString.empty
      })
      .filter(_.nonEmpty)
      .map(bytes => Try(deserializeInput(cbor, bytes.toArray)))
      .mapConcat {
        case Success(input) =>
          List(input.copy(playerId = playerId))
        case Failure(ex) =>
          system.log.warn("Invalid client input binary payload, ignoring: {}", ex.getMessage)
          Nil
      }
      .to(Sink.foreach(input => world ! GameWorldActor.ApplyInput(input)))

    val snapshotSource: Source[Message, org.apache.pekko.actor.typed.ActorRef[WorldSnapshot]] =
      org.apache.pekko.stream.typed.scaladsl.ActorSource.actorRef[WorldSnapshot](
        completionMatcher = PartialFunction.empty,
        failureMatcher = PartialFunction.empty,
        bufferSize = 64,
        overflowStrategy = OverflowStrategy.dropHead
      ).map { snap =>
        val dto = toWorldSnapshotDto(snap)
        val payload = cbor.writeValueAsBytes(dto)
        val framed = BinaryProtocol.wrap(BinaryProtocol.TYPE_SNAPSHOT, payload)
        BinaryMessage(ByteString(framed))
      }

    val welcome = {
      val dto = new WelcomeDto(playerId, null, null)
      val payload = cbor.writeValueAsBytes(dto)
      val framed = BinaryProtocol.wrap(BinaryProtocol.TYPE_WELCOME, payload)
      BinaryMessage(ByteString(framed))
    }
    val source: Source[Message, org.apache.pekko.actor.typed.ActorRef[WorldSnapshot]] =
      Source.single(welcome).concatMat(snapshotSource)(Keep.right)

    Flow.fromSinkAndSourceCoupledMat(sink, source) { (_, ref: org.apache.pekko.actor.typed.ActorRef[WorldSnapshot]) =>
      world ! GameWorldActor.RegisterSession(playerId, ref)
      ref
    }.watchTermination() { (ref: org.apache.pekko.actor.typed.ActorRef[WorldSnapshot], done) =>
      done.onComplete(_ => world ! GameWorldActor.UnregisterSession(ref))(using system.executionContext)
      ref
    }

  private def deserializeInput(cbor: ObjectMapper, bytes: Array[Byte]): ClientInput = {
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
    val movement = com.droiddungeon.server.MovementIntentDto(
      m.leftHeld(),
      m.rightHeld(),
      m.upHeld(),
      m.downHeld(),
      m.leftJustPressed(),
      m.rightJustPressed(),
      m.upJustPressed(),
      m.downJustPressed()
    )
    val weapon = com.droiddungeon.server.WeaponInputDto(w.attackJustPressed(), w.attackHeld(), w.aimWorldX(), w.aimWorldY())
    ClientInput(dto.tick(), dto.playerId(), movement, weapon, dto.drop(), dto.pickUp(), dto.mine())
  }

  private def toWorldSnapshotDto(snap: WorldSnapshot): WorldSnapshotDto = {
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

  def main(args: Array[String]): Unit =
    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "droiddungeon-server")
    implicit val ec: ExecutionContext = system.executionContext

    val seed = sys.props.get("network.seed").flatMap(s => scala.util.Try(s.toLong).toOption).getOrElse(System.currentTimeMillis())

    val itemLines =
      sys.props.get("items.path") match
        case Some(path) => Files.readAllLines(Paths.get(path)).asScala.toList
        case None =>
          val src = scala.io.Source.fromResource("items.txt")
          try src.getLines().toList
          finally src.close()

    val itemRegistry = ItemRegistry.loadDataOnly(itemLines.asJava)
    val loop = new ServerGameLoop(GameConfig.defaults(), itemRegistry, seed)

    val worldActor = system.systemActorOf(GameWorldActor(loop), "world")

    val tickRoute: Option[Route] =
      if (sys.props.get("tick.enabled").contains("true"))
        Some(
          path("tick") {
            parameter("dt".as[Double].withDefault(0.016)) { dt =>
              worldActor ! GameWorldActor.AdvanceGlobal(dt.toFloat)
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "ticked"))
            }
          }
        )
      else None

    val route: Route =
      concat(
        path("health") {
          get {
            complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "ok"))
          }
        },
        tickRoute.getOrElse(reject),
        path("ws") {
          handleWebSocketMessages(websocketFlow(worldActor)(using system))
        }
      )

    Http().newServerAt("0.0.0.0", 8080).bind(route).onComplete {
      case Success(binding) =>
        val addr = binding.localAddress
        system.log.info("Server online at http://{}:{}/", addr.getHostString, addr.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP server", ex)
        system.terminate()
    }

    sys.addShutdownHook {
      itemRegistry.close()
      system.terminate()
    }
