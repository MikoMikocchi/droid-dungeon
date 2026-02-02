package com.droiddungeon.server

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

object HttpRoutes:
  def build(
      worldActor: org.apache.pekko.actor.typed.ActorRef[GameWorldActor.Command]
  )(using system: ActorSystem[Nothing]): Route = {
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

    concat(
      path("health") {
        get {
          complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "ok"))
        }
      },
      tickRoute.getOrElse(reject),
      path("ws") {
        parameter("playerId".?) { pid =>
          handleWebSocketMessages(WebSocketSessionHandler.websocketFlow(worldActor, pid)(using system))
        }
      }
    )
  }
