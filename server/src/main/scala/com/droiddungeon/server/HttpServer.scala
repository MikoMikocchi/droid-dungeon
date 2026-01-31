package com.droiddungeon.server

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives._

import com.droiddungeon.config.GameConfig
import com.droiddungeon.input.{InputFrame, MovementIntent, WeaponInput}
import com.droiddungeon.items.ItemRegistry
import com.droiddungeon.server.ServerGameLoop

import java.nio.file.{Files, Paths}
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success}

object HttpServer:
  def main(args: Array[String]): Unit =
    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "droiddungeon-server")
    implicit val ec: ExecutionContext = system.executionContext

    val itemLines =
      sys.props.get("items.path") match
        case Some(path) => Files.readAllLines(Paths.get(path)).asScala.toList
        case None =>
          val src = scala.io.Source.fromResource("items.txt")
          try src.getLines().toList
          finally src.close()

    val itemRegistry = ItemRegistry.loadDataOnly(itemLines.asJava)
    val loop = new ServerGameLoop(GameConfig.defaults(), itemRegistry, System.currentTimeMillis())

    val emptyInput = InputFrame.serverFrame(
      new MovementIntent(false, false, false, false, false, false, false, false),
      WeaponInput.idle(),
      false,
      false,
      false
    )

    val route =
      concat(
        path("health") {
          get {
            complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "ok"))
          }
        },
        path("tick") {
          parameters("dt".as[Double].withDefault(0.016)) { dt =>
            loop.tick(emptyInput, dt.toFloat)
            complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "ticked"))
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
