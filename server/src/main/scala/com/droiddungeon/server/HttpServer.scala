package com.droiddungeon.server

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import com.droiddungeon.config.GameConfig
import com.droiddungeon.server.ServerGameLoop
import com.droiddungeon.server.{HttpRoutes, ItemRegistryLoader}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object HttpServer:
  def main(args: Array[String]): Unit =
    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "droiddungeon-server")
    implicit val ec: ExecutionContext = system.executionContext

    val seed = sys.props.get("network.seed").flatMap(s => scala.util.Try(s.toLong).toOption).getOrElse(System.currentTimeMillis())

    val itemRegistry = ItemRegistryLoader.load()
    val loop = new ServerGameLoop(GameConfig.defaults(), itemRegistry, seed)

    val worldActor = system.systemActorOf(GameWorldActor(loop), "world")

    val route = HttpRoutes.build(worldActor)

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
