package com.droiddungeon.server

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.model.ws.{BinaryMessage, Message}
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.apache.pekko.util.ByteString

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}

object WebSocketSessionHandler:
  def websocketFlow(
      world: org.apache.pekko.actor.typed.ActorRef[GameWorldActor.Command],
      requestedPlayerId: Option[String]
  )(using system: ActorSystem[Nothing]): Flow[Message, Message, Any] =
    import system.executionContext
    val playerId =
      requestedPlayerId.filter(id => id != null && id.nonEmpty).getOrElse(java.util.UUID.randomUUID().toString)
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
