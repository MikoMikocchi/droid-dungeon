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
import com.droiddungeon.server.ServerProtocolMapper
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import java.nio.file.{Files, Paths}
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success}
import scala.concurrent.duration.*
import scala.util.Try

object HttpServer:
  private def websocketFlow(
      world: org.apache.pekko.actor.typed.ActorRef[GameWorldActor.Command],
      requestedPlayerId: Option[String]
  )(using system: ActorSystem[Nothing]): Flow[Message, Message, Any] =
    import system.executionContext
    val playerId = requestedPlayerId.filter(id => id != null && id.nonEmpty).getOrElse(java.util.UUID.randomUUID().toString)
    val cbor = new ObjectMapper(new CBORFactory()).findAndRegisterModules()

    val sink: Sink[Message, Any] = Flow[Message]
      .collect { case bm: BinaryMessage => bm }
      .mapAsync(1)(_.toStrict(2.seconds).map(_.data).recover { case ex =>
        system.log.warn("Failed to read streamed binary message, ignoring: {}", ex.getMessage)
        ByteString.empty
      })
      .filter(_.nonEmpty)
      .map(bytes => Try(ServerProtocolMapper.deserializeInput(cbor, bytes.toArray)))
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
        ServerProtocolMapper.encodeSnapshotMessage(cbor, snap)
      }

    val welcome = ServerProtocolMapper.encodeWelcomeMessage(cbor, playerId)
    val source: Source[Message, org.apache.pekko.actor.typed.ActorRef[WorldSnapshot]] =
      Source.single(welcome).concatMat(snapshotSource)(Keep.right)

    Flow.fromSinkAndSourceCoupledMat(sink, source) { (_, ref: org.apache.pekko.actor.typed.ActorRef[WorldSnapshot]) =>
      world ! GameWorldActor.RegisterSession(playerId, ref)
      ref
    }.watchTermination() { (ref: org.apache.pekko.actor.typed.ActorRef[WorldSnapshot], done) =>
      done.onComplete(_ => world ! GameWorldActor.UnregisterSession(ref))(using system.executionContext)
      ref
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
          parameter("playerId".?) { pid =>
            handleWebSocketMessages(websocketFlow(worldActor, pid)(using system))
          }
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
