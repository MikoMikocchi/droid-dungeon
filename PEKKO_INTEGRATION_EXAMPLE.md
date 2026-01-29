# Практический пример интеграции Pekko и Scala

Этот документ содержит готовые примеры кода для быстрого старта с Pekko и Scala в проекте Droid Dungeon.

---

## Шаг 1: Обновление конфигурации Gradle

### settings.gradle.kts
```kotlin
rootProject.name = "droid-dungeon"

include("core", "desktop", "server")
```

### server/build.gradle.kts
```kotlin
plugins {
    scala
    application
}

val scalaVersion = "2.13.12"
val pekkoVersion = "1.0.2"

dependencies {
    // Зависимость от core модуля для использования общих классов
    implementation(project(":core"))
    
    // Scala runtime
    implementation("org.scala-lang:scala-library:$scalaVersion")
    
    // Apache Pekko (Actor framework)
    implementation("org.apache.pekko:pekko-actor-typed_2.13:$pekkoVersion")
    implementation("org.apache.pekko:pekko-stream_2.13:$pekkoVersion")
    implementation("org.apache.pekko:pekko-http_2.13:$pekkoVersion")
    
    // JSON serialization
    implementation("com.lihaoyi::upickle:3.1.3")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.apache.pekko:pekko-slf4j_2.13:$pekkoVersion")
    
    // Testing
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_2.13:$pekkoVersion")
    testImplementation("org.scalatest:scalatest_2.13:3.2.17")
}

application {
    mainClass.set("com.droiddungeon.server.GameServer")
}

tasks.withType<ScalaCompile>().configureEach {
    scalaCompileOptions.additionalParameters = listOf(
        "-deprecation",
        "-feature",
        "-unchecked",
        "-Xlint"
    )
}
```

---

## Шаг 2: Сетевой протокол (общие сообщения)

### core/src/main/java/com/droiddungeon/network/NetworkProtocol.java
```java
package com.droiddungeon.network;

/**
 * Определения сетевых сообщений для клиента (Java).
 * Используется Jackson/Gson для сериализации в JSON.
 */
public class NetworkProtocol {
    
    public static class PlayerMove {
        public String playerId;
        public float x;
        public float y;
        public long timestamp;
        
        public PlayerMove() {}
        
        public PlayerMove(String playerId, float x, float y, long timestamp) {
            this.playerId = playerId;
            this.x = x;
            this.y = y;
            this.timestamp = timestamp;
        }
    }
    
    public static class PlayerAction {
        public String playerId;
        public String actionType;  // "attack", "interact", "use_item"
        public float targetX;
        public float targetY;
        public String itemId;
        
        public PlayerAction() {}
        
        public PlayerAction(String playerId, String actionType, float targetX, float targetY) {
            this.playerId = playerId;
            this.actionType = actionType;
            this.targetX = targetX;
            this.targetY = targetY;
        }
    }
    
    public static class WorldState {
        public PlayerState[] players;
        public EntityState[] entities;
        public long timestamp;
        
        public WorldState() {}
    }
    
    public static class PlayerState {
        public String playerId;
        public String name;
        public float x;
        public float y;
        public float health;
        public float maxHealth;
        
        public PlayerState() {}
    }
    
    public static class EntityState {
        public String entityId;
        public String entityType;  // "enemy", "item", "companion"
        public float x;
        public float y;
        
        public EntityState() {}
    }
    
    public static class PlayerJoined {
        public String playerId;
        public String playerName;
        public long timestamp;
        
        public PlayerJoined() {}
    }
    
    public static class PlayerLeft {
        public String playerId;
        public long timestamp;
        
        public PlayerLeft() {}
    }
    
    public static class ChatMessage {
        public String playerId;
        public String message;
        public long timestamp;
        
        public ChatMessage() {}
    }
}
```

---

## Шаг 3: Протокол на стороне сервера (Scala)

### server/src/main/scala/com/droiddungeon/server/protocol/Messages.scala
```scala
package com.droiddungeon.server.protocol

import upickle.default._

/**
 * Протокол сообщений между клиентами и сервером.
 * Используется uPickle для автоматической сериализации.
 */
object Messages {
  
  // Входящие сообщения от клиента
  sealed trait ClientMessage
  
  case class PlayerMove(
    playerId: String,
    x: Float,
    y: Float,
    timestamp: Long
  ) extends ClientMessage
  
  case class PlayerAction(
    playerId: String,
    actionType: String,
    targetX: Float,
    targetY: Float,
    itemId: Option[String] = None
  ) extends ClientMessage
  
  case class ChatMessage(
    playerId: String,
    message: String,
    timestamp: Long
  ) extends ClientMessage
  
  case class JoinRequest(
    playerName: String,
    version: String
  ) extends ClientMessage
  
  // Исходящие сообщения к клиенту
  sealed trait ServerMessage
  
  case class WorldState(
    players: Seq[PlayerState],
    entities: Seq[EntityState],
    timestamp: Long
  ) extends ServerMessage
  
  case class PlayerJoined(
    playerId: String,
    playerName: String,
    timestamp: Long
  ) extends ServerMessage
  
  case class PlayerLeft(
    playerId: String,
    timestamp: Long
  ) extends ServerMessage
  
  case class ServerChatMessage(
    playerId: String,
    message: String,
    timestamp: Long
  ) extends ServerMessage
  
  case class JoinAccepted(
    playerId: String,
    worldSeed: Long,
    spawnX: Int,
    spawnY: Int
  ) extends ServerMessage
  
  case class Error(
    code: String,
    message: String
  ) extends ServerMessage
  
  // Вложенные структуры
  case class PlayerState(
    playerId: String,
    name: String,
    x: Float,
    y: Float,
    health: Float,
    maxHealth: Float
  )
  
  case class EntityState(
    entityId: String,
    entityType: String,
    x: Float,
    y: Float
  )
  
  // Автоматическая генерация serializers
  implicit val playerMoveRW: ReadWriter[PlayerMove] = macroRW
  implicit val playerActionRW: ReadWriter[PlayerAction] = macroRW
  implicit val chatMessageRW: ReadWriter[ChatMessage] = macroRW
  implicit val joinRequestRW: ReadWriter[JoinRequest] = macroRW
  
  implicit val playerStateRW: ReadWriter[PlayerState] = macroRW
  implicit val entityStateRW: ReadWriter[EntityState] = macroRW
  implicit val worldStateRW: ReadWriter[WorldState] = macroRW
  implicit val playerJoinedRW: ReadWriter[PlayerJoined] = macroRW
  implicit val playerLeftRW: ReadWriter[PlayerLeft] = macroRW
  implicit val serverChatMessageRW: ReadWriter[ServerChatMessage] = macroRW
  implicit val joinAcceptedRW: ReadWriter[JoinAccepted] = macroRW
  implicit val errorRW: ReadWriter[Error] = macroRW
  
  implicit val clientMessageRW: ReadWriter[ClientMessage] = macroRW
  implicit val serverMessageRW: ReadWriter[ServerMessage] = macroRW
}
```

---

## Шаг 4: Game Room Actor (сервер)

### server/src/main/scala/com/droiddungeon/server/actors/GameRoomActor.scala
```scala
package com.droiddungeon.server.actors

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import com.droiddungeon.server.protocol.Messages._
import scala.concurrent.duration._
import scala.collection.mutable

object GameRoomActor {
  
  sealed trait Command
  
  case class PlayerConnect(
    playerId: String, 
    playerName: String,
    replyTo: ActorRef[ServerMessage]
  ) extends Command
  
  case class PlayerDisconnect(playerId: String) extends Command
  
  case class PlayerInput(
    playerId: String,
    message: ClientMessage
  ) extends Command
  
  case object Tick extends Command
  
  // Game state
  private case class GameState(
    players: mutable.Map[String, PlayerData] = mutable.Map.empty,
    entities: mutable.Map[String, EntityData] = mutable.Map.empty,
    worldSeed: Long = System.currentTimeMillis(),
    tickCounter: Long = 0
  )
  
  private case class PlayerData(
    id: String,
    name: String,
    var x: Float,
    var y: Float,
    var health: Float,
    maxHealth: Float = 100f,
    connection: ActorRef[ServerMessage]
  )
  
  private case class EntityData(
    id: String,
    entityType: String,
    var x: Float,
    var y: Float
  )
  
  def apply(roomId: String): Behavior[Command] = {
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        // Tick every 50ms (20 ticks per second)
        timers.startTimerWithFixedDelay(Tick, 50.milliseconds)
        
        context.log.info(s"Game room $roomId started")
        
        running(roomId, GameState(), timers)
      }
    }
  }
  
  private def running(
    roomId: String, 
    state: GameState,
    timers: TimerScheduler[Command]
  ): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case PlayerConnect(playerId, playerName, replyTo) =>
          context.log.info(s"Player $playerId ($playerName) connecting")
          
          // Create player data
          val player = PlayerData(
            id = playerId,
            name = playerName,
            x = 0f,
            y = 0f,
            health = 100f,
            connection = replyTo
          )
          
          state.players.put(playerId, player)
          
          // Send join accepted
          replyTo ! JoinAccepted(
            playerId = playerId,
            worldSeed = state.worldSeed,
            spawnX = 0,
            spawnY = 0
          )
          
          // Notify other players
          val joinMsg = PlayerJoined(playerId, playerName, System.currentTimeMillis())
          state.players.values.foreach { p =>
            if (p.id != playerId) {
              p.connection ! joinMsg
            }
          }
          
          Behaviors.same
          
        case PlayerDisconnect(playerId) =>
          context.log.info(s"Player $playerId disconnecting")
          
          state.players.remove(playerId)
          
          // Notify other players
          val leaveMsg = PlayerLeft(playerId, System.currentTimeMillis())
          state.players.values.foreach(_.connection ! leaveMsg)
          
          Behaviors.same
          
        case PlayerInput(playerId, clientMessage) =>
          clientMessage match {
            case PlayerMove(_, x, y, _) =>
              state.players.get(playerId).foreach { player =>
                player.x = x
                player.y = y
              }
              
            case PlayerAction(_, actionType, targetX, targetY, itemId) =>
              context.log.debug(s"Player $playerId action: $actionType at ($targetX, $targetY)")
              // Handle action logic here
              
            case ChatMessage(_, msg, _) =>
              // Broadcast chat
              val chatMsg = ServerChatMessage(playerId, msg, System.currentTimeMillis())
              state.players.values.foreach(_.connection ! chatMsg)
              
            case _ =>
              context.log.warn(s"Unknown client message: $clientMessage")
          }
          
          Behaviors.same
          
        case Tick =>
          state.tickCounter += 1
          
          // Update game logic (enemies, physics, etc.)
          updateGameLogic(state)
          
          // Send world state to all players (every 3rd tick = ~15 updates/sec)
          if (state.tickCounter % 3 == 0) {
            broadcastWorldState(state)
          }
          
          Behaviors.same
      }
    }
  }
  
  private def updateGameLogic(state: GameState): Unit = {
    // Update enemies, items, etc.
    // This would integrate with your Java game logic
  }
  
  private def broadcastWorldState(state: GameState): Unit = {
    val worldState = WorldState(
      players = state.players.values.map { p =>
        PlayerState(p.id, p.name, p.x, p.y, p.health, p.maxHealth)
      }.toSeq,
      entities = state.entities.values.map { e =>
        EntityState(e.id, e.entityType, e.x, e.y)
      }.toSeq,
      timestamp = System.currentTimeMillis()
    )
    
    state.players.values.foreach(_.connection ! worldState)
  }
}
```

---

## Шаг 5: WebSocket Handler

### server/src/main/scala/com/droiddungeon/server/http/WebSocketHandler.scala
```scala
package com.droiddungeon.server.http

import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import org.apache.pekko.stream.{Materializer, OverflowStrategy}
import org.apache.pekko.util.Timeout
import com.droiddungeon.server.actors.GameRoomActor
import com.droiddungeon.server.protocol.Messages._
import upickle.default._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class WebSocketHandler(
  gameRoom: ActorRef[GameRoomActor.Command]
)(implicit system: ActorSystem[_], mat: Materializer, ec: ExecutionContext) {
  
  implicit val timeout: Timeout = 3.seconds
  
  def routes: Route = {
    path("game" / "join") {
      parameters("name") { playerName =>
        handleWebSocketMessages(createGameFlow(playerName))
      }
    }
  }
  
  private def createGameFlow(playerName: String): Flow[Message, Message, Any] = {
    val playerId = java.util.UUID.randomUUID().toString
    
    // Create source for outgoing messages
    val (outActor, outSource) = Source
      .actorRef[ServerMessage](
        completionMatcher = PartialFunction.empty,
        failureMatcher = PartialFunction.empty,
        bufferSize = 100,
        overflowStrategy = OverflowStrategy.dropHead
      )
      .preMaterialize()
    
    // Connect player to game room
    gameRoom ! GameRoomActor.PlayerConnect(playerId, playerName, outActor)
    
    // Create sink for incoming messages
    val inSink = Sink.foreach[Message] {
      case TextMessage.Strict(text) =>
        try {
          val clientMessage = read[ClientMessage](text)
          gameRoom ! GameRoomActor.PlayerInput(playerId, clientMessage)
        } catch {
          case ex: Exception =>
            system.log.error(s"Failed to parse message: $text", ex)
        }
        
      case _ =>
        system.log.warn("Unsupported message type")
    }
    
    // Disconnect on completion
    val enrichedSink = inSink.watchTermination() { (_, done) =>
      done.onComplete {
        case Success(_) | Failure(_) =>
          gameRoom ! GameRoomActor.PlayerDisconnect(playerId)
      }
    }
    
    // Convert ServerMessage to TextMessage
    val messageSource = outSource.map { msg =>
      TextMessage(write(msg))
    }
    
    Flow.fromSinkAndSource(enrichedSink, messageSource)
  }
}
```

---

## Шаг 6: Main Server

### server/src/main/scala/com/droiddungeon/server/GameServer.scala
```scala
package com.droiddungeon.server

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives._
import com.droiddungeon.server.actors.GameRoomActor
import com.droiddungeon.server.http.WebSocketHandler

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn
import scala.util.{Failure, Success}

object GameServer {
  
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "game-server")
    implicit val ec: ExecutionContextExecutor = system.executionContext
    
    // Create game room actor
    val gameRoom = system.systemActorOf(GameRoomActor("main-room"), "game-room")
    
    // Create WebSocket handler
    val wsHandler = new WebSocketHandler(gameRoom)
    
    // Define routes
    val routes = 
      pathPrefix("api") {
        wsHandler.routes ~
        path("health") {
          get {
            complete("OK")
          }
        }
      }
    
    // Start HTTP server
    val bindingFuture: Future[Http.ServerBinding] = 
      Http().newServerAt("0.0.0.0", 8080).bind(routes)
    
    bindingFuture.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"Server online at http://${address.getHostString}:${address.getPort}/")
        system.log.info(s"WebSocket endpoint: ws://${address.getHostString}:${address.getPort}/api/game/join?name=PLAYER_NAME")
        
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP server", ex)
        system.terminate()
    }
    
    // Graceful shutdown
    sys.addShutdownHook {
      bindingFuture
        .flatMap(_.unbind())
        .onComplete(_ => system.terminate())
    }
  }
}
```

---

## Шаг 7: Клиентская часть (Java)

### core/src/main/java/com/droiddungeon/network/NetworkClient.java
```java
package com.droiddungeon.network;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.utils.Json;
import com.droiddungeon.network.NetworkProtocol.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * TCP Socket client for connecting to game server.
 * Uses LibGDX Net API for cross-platform TCP connections.
 * Note: This is a simplified example. For WebSocket, consider using a dedicated library.
 */
public class NetworkClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread receiveThread;
    private final ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<>();
    private final Json json = new Json();
    private boolean connected = false;
    
    private MessageHandler handler;
    
    public interface MessageHandler {
        void onWorldState(WorldState state);
        void onPlayerJoined(PlayerJoined joined);
        void onPlayerLeft(PlayerLeft left);
        void onChatMessage(ChatMessage message);
    }
    
    public void connect(String host, int port, String playerName, MessageHandler handler) {
        this.handler = handler;
        
        try {
            // Use LibGDX socket for cross-platform support
            socket = Gdx.net.newClientSocket(Net.Protocol.TCP, host, port, null);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            connected = true;
            
            // Start receive thread
            receiveThread = new Thread(this::receiveLoop);
            receiveThread.setDaemon(true);
            receiveThread.start();
            
            Gdx.app.log("NetworkClient", "Connected to " + host + ":" + port);
            
        } catch (Exception e) {
            connected = false;
            Gdx.app.error("NetworkClient", "Failed to connect", e);
        }
    }
    
    public void disconnect() {
        connected = false;
        
        if (socket != null) {
            socket.dispose();
        }
        
        if (receiveThread != null) {
            receiveThread.interrupt();
        }
    }
    
    public void sendPlayerMove(String playerId, float x, float y) {
        PlayerMove move = new PlayerMove(playerId, x, y, System.currentTimeMillis());
        send(json.toJson(move));
    }
    
    public void sendPlayerAction(String playerId, String actionType, float targetX, float targetY) {
        PlayerAction action = new PlayerAction(playerId, actionType, targetX, targetY);
        send(json.toJson(action));
    }
    
    private void send(String message) {
        if (connected && out != null) {
            out.println(message);
        }
    }
    
    private void receiveLoop() {
        try {
            String line;
            while (connected && (line = in.readLine()) != null) {
                messageQueue.offer(line);
            }
        } catch (IOException e) {
            if (connected) {
                Gdx.app.error("NetworkClient", "Connection lost", e);
            }
        }
    }
    
    /**
     * Process received messages. Call this from the game render thread!
     */
    public void update() {
        String message;
        while ((message = messageQueue.poll()) != null) {
            handleMessage(message);
        }
    }
    
    private void handleMessage(String jsonMessage) {
        try {
            // TODO: Use proper type discriminator field for reliable message type detection
            // Current implementation uses field presence detection as a simplified example
            if (jsonMessage.contains("\"players\"")) {
                WorldState state = json.fromJson(WorldState.class, jsonMessage);
                if (handler != null) handler.onWorldState(state);
            } 
            else if (jsonMessage.contains("\"playerJoined\"")) {
                PlayerJoined joined = json.fromJson(PlayerJoined.class, jsonMessage);
                if (handler != null) handler.onPlayerJoined(joined);
            }
            else if (jsonMessage.contains("\"playerLeft\"")) {
                PlayerLeft left = json.fromJson(PlayerLeft.class, jsonMessage);
                if (handler != null) handler.onPlayerLeft(left);
            }
            else if (jsonMessage.contains("\"message\"")) {
                ChatMessage chat = json.fromJson(ChatMessage.class, jsonMessage);
                if (handler != null) handler.onChatMessage(chat);
            }
        } catch (Exception e) {
            Gdx.app.error("NetworkClient", "Failed to parse message: " + jsonMessage, e);
        }
    }
    
    public boolean isConnected() {
        return connected;
    }
}
```

---

## Шаг 8: Запуск

### Запуск сервера
```bash
# Из корневой директории проекта
./gradlew :server:run

# Сервер запустится на http://localhost:8080
# WebSocket endpoint: ws://localhost:8080/api/game/join?name=YourName
```

### Тестирование с помощью wscat
```bash
# Установить wscat
npm install -g wscat

# Подключиться к серверу
wscat -c "ws://localhost:8080/api/game/join?name=TestPlayer"

# Отправить сообщение перемещения
{"playerId":"test","x":10.5,"y":20.3,"timestamp":1234567890}
```

---

## Шаг 9: Интеграция в GameRuntime

### Модификация GameRuntime для мультиплеера
```java
package com.droiddungeon.runtime;

import com.droiddungeon.network.NetworkClient;
import com.droiddungeon.network.NetworkProtocol.*;
import java.util.HashMap;
import java.util.Map;

public class MultiplayerGameRuntime extends GameRuntime {
    private NetworkClient networkClient;
    private String localPlayerId;
    private Map<String, RemotePlayer> remotePlayers = new HashMap<>();
    
    public MultiplayerGameRuntime(GameConfig config, String serverHost, int serverPort, String playerName) {
        super(config);
        
        // Create and connect network client
        networkClient = new NetworkClient();
        networkClient.connect(serverHost, serverPort, playerName, new NetworkClient.MessageHandler() {
            @Override
            public void onWorldState(WorldState state) {
                updateRemotePlayers(state);
            }
            
            @Override
            public void onPlayerJoined(PlayerJoined joined) {
                addRemotePlayer(joined.playerId, joined.playerName);
            }
            
            @Override
            public void onPlayerLeft(PlayerLeft left) {
                removeRemotePlayer(left.playerId);
            }
            
            @Override
            public void onChatMessage(ChatMessage message) {
                // Handle chat
            }
        });
    }
    
    @Override
    public void render() {
        // Process network messages first
        if (networkClient != null) {
            networkClient.update();
        }
        
        // Send local player position
        if (localPlayerId != null && context != null) {
            Player localPlayer = context.player();
            networkClient.sendPlayerMove(
                localPlayerId, 
                localPlayer.getRenderX(), 
                localPlayer.getRenderY()
            );
        }
        
        // Continue with normal render
        super.render();
        
        // Render remote players
        renderRemotePlayers();
    }
    
    private void updateRemotePlayers(WorldState state) {
        for (PlayerState ps : state.players) {
            if (!ps.playerId.equals(localPlayerId)) {
                RemotePlayer remote = remotePlayers.get(ps.playerId);
                if (remote != null) {
                    remote.updatePosition(ps.x, ps.y);
                    remote.setHealth(ps.health);
                }
            }
        }
    }
    
    private void addRemotePlayer(String playerId, String playerName) {
        remotePlayers.put(playerId, new RemotePlayer(playerId, playerName));
    }
    
    private void removeRemotePlayer(String playerId) {
        remotePlayers.remove(playerId);
    }
    
    private void renderRemotePlayers() {
        // Render each remote player using your existing rendering system
        for (RemotePlayer player : remotePlayers.values()) {
            // Use your WorldRenderer to draw the player sprite
        }
    }
    
    @Override
    public void dispose() {
        if (networkClient != null) {
            networkClient.disconnect();
        }
        super.dispose();
    }
}
```

---

## Полезные команды

### Сборка проекта
```bash
./gradlew build
```

### Запуск тестов
```bash
./gradlew test
```

### Запуск сервера в dev режиме
```bash
./gradlew :server:run
```

### Запуск клиента
```bash
./gradlew :desktop:run
```

---

## Дальнейшие улучшения

1. **Аутентификация**: Добавить JWT токены для безопасности
2. **Reconnect логика**: Восстановление соединения при обрыве
3. **Client-side prediction**: Мгновенная реакция на локальный input
4. **Interpolation**: Плавное движение удаленных игроков
5. **Compression**: Сжатие сообщений для экономии трафика
6. **Rate limiting**: Защита от флуда
7. **Monitoring**: Prometheus метрики для мониторинга сервера

---

**Готово к использованию!** 🚀

Этот пример предоставляет полную базовую инфраструктуру для добавления мультиплеера в Droid Dungeon с использованием Pekko и Scala.
