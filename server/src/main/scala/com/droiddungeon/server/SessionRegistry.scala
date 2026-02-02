package com.droiddungeon.server

import org.apache.pekko.actor.typed.ActorRef

final case class SessionRegistry(
    sessions: Map[String, ActorRef[WorldSnapshot]]
) {
  def register(playerId: String, ref: ActorRef[WorldSnapshot]): SessionRegistry =
    copy(sessions = sessions + (playerId -> ref))

  def unregister(ref: ActorRef[WorldSnapshot]): (SessionRegistry, Set[String]) = {
    val removedPlayers = sessions.collect { case (pid, r) if r == ref => pid }.toSet
    val remaining = sessions.filterNot { case (_, r) => r == ref }
    (copy(sessions = remaining), removedPlayers)
  }

  def contains(playerId: String): Boolean = sessions.contains(playerId)
}

object SessionRegistry {
  val empty: SessionRegistry = SessionRegistry(Map.empty)
}
