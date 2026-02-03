package com.droiddungeon.server

import com.droiddungeon.net.codec.{CborProtocolCodec, ProtocolCodec}
import com.droiddungeon.net.dto.{ClientInputDto, WelcomeDto, WorldSnapshotDto}
import java.nio.ByteBuffer
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.model.ws.{BinaryMessage, Message}
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.apache.pekko.util.ByteString
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
    val codec: ProtocolCodec = CborProtocolCodec.createDefault()

    val sink: Sink[Message, Any] = Flow[Message]
      .collect { case bm: BinaryMessage => bm }
      .mapAsync(1)(_.toStrict(2.seconds).map(_.data).recover { case ex =>
        system.log.warn("Failed to read streamed binary message, ignoring: {}", ex.getMessage)
        ByteString.empty
      })
      .filter(_.nonEmpty)
      .map(bytes => Try(codec.decode(ByteBuffer.wrap(bytes.toArray))))
      .mapConcat {
        case Success(msg: ProtocolCodec.InputMessage) =>
          val input = msg.value()
          List(
            new ClientInputDto(
              input.tick(),
              playerId,
              input.movement(),
              input.weapon(),
              input.drop(),
              input.pickUp(),
              input.mine()
            )
          )
        case Success(_) =>
          Nil
        case Failure(ex) =>
          system.log.warn("Invalid client input binary payload, ignoring: {}", ex.getMessage)
          Nil
      }
      .to(Sink.foreach(input => world ! GameWorldActor.ApplyInput(input)))

    val snapshotSource: Source[Message, org.apache.pekko.actor.typed.ActorRef[WorldSnapshotDto]] =
      org.apache.pekko.stream.typed.scaladsl.ActorSource.actorRef[WorldSnapshotDto](
        completionMatcher = PartialFunction.empty,
        failureMatcher = PartialFunction.empty,
        bufferSize = 64,
        overflowStrategy = OverflowStrategy.dropHead
      ).map { snap =>
        BinaryMessage(ByteString(codec.encodeSnapshot(snap)))
      }

    val welcome = BinaryMessage(ByteString(codec.encodeWelcome(new WelcomeDto(playerId, null, null))))
    val source: Source[Message, org.apache.pekko.actor.typed.ActorRef[WorldSnapshotDto]] =
      Source.single(welcome).concatMat(snapshotSource)(Keep.right)

    Flow.fromSinkAndSourceCoupledMat(sink, source) { (_, ref: org.apache.pekko.actor.typed.ActorRef[WorldSnapshotDto]) =>
      world ! GameWorldActor.RegisterSession(playerId, ref)
      ref
    }.watchTermination() { (ref: org.apache.pekko.actor.typed.ActorRef[WorldSnapshotDto], done) =>
      done.onComplete(_ => world ! GameWorldActor.UnregisterSession(ref))(using system.executionContext)
      ref
    }
