# Анализ возможности использования Pekko и Scala для мультиплеера

## Резюме

**✅ ДА, Pekko и Scala можно использовать для создания мультиплеера в Droid Dungeon**

Эта интеграция технически возможна и имеет смысл для добавления мультиплеерных возможностей в текущую архитектуру игры на Java + LibGDX.

---

## Текущее состояние проекта

### Технический стек
- **Язык**: Java 21
- **Игровой движок**: LibGDX 1.12.1
- **Сборка**: Gradle (Kotlin DSL)
- **Архитектура**: Однопоточная, клиентская игра (одиночный игрок)

### Архитектура игры
```
DroidDungeonGame (LibGDX entry point)
    └── GameRuntime (orchestration shell)
        ├── GameUpdater (game logic)
        ├── GameRenderCoordinator (rendering)
        └── GameContext (immutable state record)
            ├── Player, Grid, EntityWorld
            ├── EnemySystem, WeaponSystem, InventorySystem
            └── CompanionSystem
```

### Ключевые особенности
- **Система сущностей**: `EntityWorld` - реестр с пространственной индексацией
- **Управление состоянием**: Централизованное через `GameContext` (Java record)
- **Обновление**: Кадрово-синхронное (`render()` вызывается каждый кадр)
- **Генерация мира**: Процедурная, на основе seed
- **Сетевого кода нет**: Полностью локальная игра

---

## Почему Pekko и Scala подходят

### Apache Pekko
[Apache Pekko](https://pekko.apache.org/) - это форк Akka, обеспечивающий:

1. **Модель акторов** - идеальна для:
   - Управления состоянием игроков (каждый игрок = актор)
   - Комнаты/лобби игр (GameRoom акторы)
   - Синхронизации состояния между клиентами
   - Обработки сетевых сообщений асинхронно

2. **Pekko Streams** - для:
   - WebSocket соединений с клиентами
   - Потоковой передачи состояния мира
   - Back-pressure управления сетевым трафиком

3. **Pekko HTTP** - для:
   - REST API (лобби, список серверов, матчмейкинг)
   - WebSocket endpoints для игрового трафика
   - Аутентификация и авторизация

4. **Pekko Cluster** (опционально) - для:
   - Горизонтального масштабирования серверов
   - Распределенной обработки множества игровых комнат
   - Отказоустойчивости

### Scala
Scala отлично работает с существующим Java кодом:

1. **Полная Java-совместимость**:
   - Вызов Java классов из Scala без проблем
   - Использование LibGDX напрямую
   - Scala и Java классы в одном проекте

2. **Преимущества языка**:
   - Case classes для сетевых сообщений
   - Pattern matching для обработки событий
   - Функциональное программирование для логики синхронизации
   - Immutability по умолчанию (безопасность в многопоточности)

3. **Экосистема**:
   - Отличная работа с Gradle/sbt
   - Богатые библиотеки для сериализации (upickle, circe)
   - Akka/Pekko идиоматично на Scala

---

## Предлагаемая архитектура мультиплеера

### Высокоуровневый дизайн

```
┌─────────────────────────────────────────────────────────────┐
│                    Client (Java + LibGDX)                    │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  DroidDungeonGame                                      │ │
│  │    ├── LocalPlayer (local input)                       │ │
│  │    ├── RemotePlayerProxy[] (network state)             │ │
│  │    └── NetworkClient (WebSocket → Server)              │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                            ↕ WebSocket
┌─────────────────────────────────────────────────────────────┐
│            Server (Scala + Pekko)                            │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Pekko HTTP Server                                     │ │
│  │    └── WebSocket Handler                               │ │
│  │          ↓                                              │ │
│  │  GameRoomActor (per game instance)                     │ │
│  │    ├── PlayerActor (per connected player)              │ │
│  │    ├── GameStateActor (authoritative state)            │ │
│  │    └── EntitySyncActor (enemy, item sync)              │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Компоненты

#### 1. Серверная часть (новый Scala модуль)
```
server/
├── build.gradle.kts (Scala + Pekko зависимости)
└── src/main/scala/com/droiddungeon/server/
    ├── GameServer.scala              // Main entry point
    ├── actors/
    │   ├── GameRoomActor.scala       // Управляет одной игровой сессией
    │   ├── PlayerActor.scala         // Представляет одного игрока
    │   └── GameStateActor.scala      // Авторитативное состояние игры
    ├── protocol/
    │   ├── Messages.scala            // Case classes для сетевых сообщений
    │   └── Serialization.scala       // JSON сериализация
    └── http/
        └── WebSocketHandler.scala    // Pekko HTTP WebSocket routes
```

#### 2. Клиентская часть (изменения в Java)
```
core/src/main/java/com/droiddungeon/
├── network/
│   ├── NetworkClient.java            // WebSocket клиент
│   ├── MessageHandler.java           // Обработка входящих сообщений
│   └── NetworkProtocol.java          // Определения сообщений (Java)
├── entity/
│   ├── RemotePlayer.java             // Представление удаленных игроков
│   └── NetworkEntity.java            // Базовый класс для сетевых сущностей
└── runtime/
    └── MultiplayerGameRuntime.java   // Расширение GameRuntime для MP
```

---

## План реализации

### Фаза 1: Настройка инфраструктуры
- [ ] Добавить Scala support в Gradle build
- [ ] Создать новый `server` модуль
- [ ] Добавить зависимости Pekko (pekko-actor, pekko-http, pekko-stream)
- [ ] Настроить базовый HTTP сервер с health check endpoint

### Фаза 2: Протокол и сериализация
- [ ] Определить сетевой протокол (case classes в Scala)
- [ ] Создать Java эквиваленты для клиента
- [ ] Реализовать JSON сериализацию (upickle/Jackson)
- [ ] Примеры сообщений:
  ```scala
  sealed trait GameMessage
  case class PlayerMove(playerId: String, x: Int, y: Int, timestamp: Long) extends GameMessage
  case class PlayerAction(playerId: String, action: String, targetX: Int, targetY: Int) extends GameMessage
  case class WorldState(players: Seq[PlayerState], entities: Seq[EntityState]) extends GameMessage
  case class PlayerJoined(playerId: String, name: String) extends GameMessage
  case class PlayerLeft(playerId: String) extends GameMessage
  ```

### Фаза 3: Акторная система (Сервер)
- [ ] Реализовать `GameRoomActor`:
  ```scala
  class GameRoomActor extends Actor {
    private var players = Map.empty[String, ActorRef]
    private var gameState: GameState = GameState.initial()
    
    def receive: Receive = {
      case PlayerJoin(id, ref) => 
        players += (id -> ref)
        ref ! WorldState(gameState)
      
      case PlayerMove(id, x, y, ts) =>
        gameState = gameState.updatePlayer(id, x, y)
        broadcast(WorldState(gameState))
      
      case Tick =>
        gameState = gameState.update()
        broadcast(WorldState(gameState))
    }
  }
  ```

- [ ] Реализовать `PlayerActor` для управления соединением
- [ ] Добавить tick loop (например, 20 обновлений/сек)

### Фаза 4: WebSocket интеграция
- [ ] Pekko HTTP WebSocket маршруты:
  ```scala
  val route =
    path("game" / "join") {
      handleWebSocketMessages(gameFlow)
    }
  
  def gameFlow: Flow[Message, Message, Any] = {
    Flow[Message]
      .collect { case TextMessage.Strict(text) => parseMessage(text) }
      .via(gameRoomActor)
      .map(msg => TextMessage(serialize(msg)))
  }
  ```

### Фаза 5: Клиентская интеграция
- [ ] Добавить WebSocket клиент в Java (можно использовать Java 11 `HttpClient` или LibGDX Net)
- [ ] Создать `NetworkClient` класс:
  ```java
  public class NetworkClient {
      private WebSocket webSocket;
      private MessageHandler handler;
      
      public void connect(String serverUrl) { ... }
      public void send(GameMessage message) { ... }
      public void handleMessage(String json) { ... }
  }
  ```

- [ ] Интегрировать в `GameRuntime`:
  - Отправка локального input на сервер
  - Получение world state updates
  - Рендеринг удаленных игроков

### Фаза 6: Синхронизация состояния
- [ ] Определить авторитативную модель (Server-authoritative)
- [ ] Клиентская предсказание (Client-side prediction):
  - Мгновенное применение локального input
  - Коррекция при получении серверного состояния
- [ ] Интерполация для удаленных игроков
- [ ] Компенсация лагов (lag compensation)

### Фаза 7: Тестирование и оптимизация
- [ ] Unit тесты для акторов (Pekko TestKit)
- [ ] Интеграционные тесты (несколько клиентов)
- [ ] Load testing (много игроков/комнат)
- [ ] Оптимизация частоты обновлений и размера сообщений

---

## Пример конфигурации Gradle

### server/build.gradle.kts
```kotlin
plugins {
    scala
    application
}

val scalaVersion = "2.13.12"
val pekkoVersion = "1.0.2"

dependencies {
    implementation(project(":core"))
    
    // Scala
    implementation("org.scala-lang:scala-library:$scalaVersion")
    
    // Pekko
    implementation("org.apache.pekko:pekko-actor_2.13:$pekkoVersion")
    implementation("org.apache.pekko:pekko-stream_2.13:$pekkoVersion")
    implementation("org.apache.pekko:pekko-http_2.13:$pekkoVersion")
    
    // JSON
    implementation("com.lihaoyi::upickle:3.1.3")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // Testing
    testImplementation("org.apache.pekko:pekko-testkit_2.13:$pekkoVersion")
    testImplementation("org.scalatest:scalatest_2.13:3.2.17")
}

application {
    mainClass.set("com.droiddungeon.server.GameServer")
}
```

### settings.gradle.kts
```kotlin
rootProject.name = "droid-dungeon"

include("core", "desktop", "server")
```

---

## Альтернативные подходы

### 1. Чистый Java подход
**За:**
- Не нужно добавлять Scala
- Проще для существующей команды

**Против:**
- Нет встроенной акторной модели
- Нужно вручную реализовывать actor-like паттерны
- Меньше функциональных возможностей

**Библиотеки**: Netty, Spring WebFlux, Vert.x

### 2. Kotlin + Ktor
**За:**
- Современный язык, похож на Scala
- Coroutines для асинхронности
- Хорошая Java совместимость

**Против:**
- Нет акторной модели (нужна доп. библиотека)
- Меньше зрелых библиотек для игровых серверов

### 3. С++ игровой сервер
**За:**
- Максимальная производительность
- Низкие задержки

**Против:**
- Сложнее интеграция с Java кодом
- Дольше разработка
- Больше возможностей для ошибок

---

## Рекомендации

### Для маленького проекта / прототипа
1. **Используйте Pekko + Scala** - быстрая разработка, надежная модель акторов
2. Начните с простой архитектуры (1 GameRoomActor)
3. Используйте WebSocket для простоты
4. Server-authoritative модель (клиент только отправляет input)

### Для продакшн-игры
1. **Pekko Cluster** для масштабирования
2. Добавьте аутентификацию (JWT токены)
3. Реализуйте reconnect логику
4. Мониторинг и логирование (Prometheus + Grafana)
5. Rate limiting и anti-cheat меры

### Для обучения
1. Начните с простого chat-сервера на Pekko
2. Добавьте broadcast состояния игры
3. Постепенно добавляйте сложность (prediction, interpolation)
4. Изучите [Pekko documentation](https://pekko.apache.org/docs/pekko/current/)

---

## Потенциальные проблемы

### 1. Синхронизация процедурной генерации
**Проблема**: Разные клиенты могут генерировать разные миры из-за различий в Java Random.

**Решение**:
- Сервер генерирует мир и отправляет chunk data
- ИЛИ гарантировать идентичную генерацию (фиксированная имплементация, синхронизированные seeds)

### 2. LibGDX - однопоточный
**Проблема**: LibGDX не thread-safe, а Pekko асинхронный.

**Решение**:
- Network код в отдельном потоке
- Обновления состояния через thread-safe очередь
- Применение изменений в LibGDX render thread

### 3. Латентность и предсказание
**Проблема**: Задержки сети делают игру неотзывчивой.

**Решение**:
- Client-side prediction для локального игрока
- Интерполация для удаленных игроков
- Lag compensation для попаданий

### 4. Масштабирование
**Проблема**: Один сервер не справится с тысячами игроков.

**Решение**:
- Pekko Cluster для распределения нагрузки
- Load balancer перед серверами
- Sharding по GameRooms

---

## Заключение

**✅ Да, Pekko и Scala - отличный выбор для добавления мультиплеера в Droid Dungeon**

### Почему это работает:
- ✅ Полная совместимость с существующим Java кодом
- ✅ Акторная модель идеальна для игровых серверов
- ✅ Зрелая экосистема (Pekko - продолжение Akka)
- ✅ Горизонтальное масштабирование из коробки
- ✅ Отличная поддержка WebSocket и HTTP

### Следующие шаги:
1. Создайте proof-of-concept с простым эхо-сервером
2. Добавьте базовую синхронизацию позиций игроков
3. Протестируйте на 2-4 клиентах
4. Постепенно добавляйте complexity

### Полезные ресурсы:
- [Apache Pekko Documentation](https://pekko.apache.org/docs/)
- [Pekko HTTP Guide](https://pekko.apache.org/docs/pekko-http/current/)
- [Scala with Gradle](https://docs.gradle.org/current/userguide/scala_plugin.html)
- [Fast-Paced Multiplayer Series](https://www.gabrielgambetta.com/client-server-game-architecture.html)
- [Source Multiplayer Networking](https://developer.valvesoftware.com/wiki/Source_Multiplayer_Networking)

---

**Автор**: GitHub Copilot
**Дата**: 29 января 2026
