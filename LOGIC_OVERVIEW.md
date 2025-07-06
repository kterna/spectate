# Spectate Mod - 完美复现开发指南

## 1. 简介

本文档是一份详尽的开发指南，旨在引导开发者从零开始，一步步地完美复现 Spectate Mod 的所有功能。文档将深入剖析其架构设计、核心逻辑和实现细节。

## 2. 架构概览

Spectate Mod 采用经典的三层分层架构，实现了逻辑与数据的清晰分离，使得代码易于理解和维护。

*   **命令层 (Command Layer):** 用户交互的入口，负责解析玩家输入的命令并调用相应的服务。
*   **服务层 (Service Layer):** 核心业务逻辑层，处理所有与"观察"相关的复杂操作，不关心数据具体如何存储。
*   **数据层 (Data Layer):** 负责数据的持久化和读取，为服务层提供数据支持。

### 组件关系图

```mermaid
graph TD
    subgraph Command Layer
        SpectateCommand["/cspectate Command"]
    end

    subgraph Service Layer
        ServerSpectateManager["ServerSpectateManager (Singleton)<br/>管理所有动态会话和核心观察逻辑"]
        SpectatePointManager["SpectatePointManager (Singleton)<br/>管理观察点的增删改查"]
    end

    subgraph Data Layer
        SpectateStateSaver["SpectateStateSaver (Singleton)<br/>负责与文件系统交互"]
        PropertiesFiles["[物理文件]<br/>spectate_points.properties<br/>cycle_lists.properties"]
    end

    subgraph In-Memory State (会话)
        PlayerSpectateSession["PlayerSpectateSession<br/>存储玩家原始状态和当前观察目标"]
        PlayerCycleSession["PlayerCycleSession<br/>存储玩家循环列表的进度"]
    end

    SpectateCommand -- 调用 --> ServerSpectateManager
    SpectateCommand -- 调用 --> SpectatePointManager

    ServerSpectateManager -- 使用 --> SpectatePointManager
    ServerSpectateManager -- 依赖 --> SpectateStateSaver
    ServerSpectateManager -- 创建/销毁 --> PlayerSpectateSession
    ServerSpectateManager -- 创建/销毁 --> PlayerCycleSession
    
    SpectatePointManager -- 依赖 --> SpectateStateSaver
    SpectateStateSaver -- 读/写 --> PropertiesFiles
```

---

## 3. 分步实施指南

按照以下步骤进行开发，可以最高效、清晰地完成整个 Mod 的复现。

### 步骤一：构建数据基础

此阶段的目标是创建数据存储的骨架。

1.  **定义数据结构:**
    *   创建 `SpectatePointData` 类，包含 `position`, `distance`, `heightOffset`, `description` 等字段。这是定义一个"观察点"的基础。
    *   (可选) 创建 `PlayerData` 类，虽然核心逻辑不直接依赖它进行文件存储，但定义它有助于理解玩家状态的构成。

2.  **实现数据持久化 (`SpectateStateSaver`):**
    *   将其设计为**单例**。
    *   实现加载 (`load...`) 和保存 (`save...`) 方法，用于读写 `spectate_points.properties` 和 `cycle_lists.properties`。
    *   提供公共接口，如 `addSpectatePoint`, `removeSpectatePoint`, `getPlayerCycleList` 等，供服务层调用。**注意：** 所有写操作都应立即保存到文件，以防数据丢失。
    - 在加载观察点时，如果文件为空，应自动创建并保存一个默认的观察点（如"origin"）。

### 步骤二：实现观察点管理

此阶段专注于实现对静态观察点的管理功能。

1.  **创建 `SpectatePointManager`:**
    *   同样设计为**单例**。
    *   它的职责是作为 `SpectateStateSaver` 的一层封装，提供更纯粹的业务接口，如 `addPoint`, `getPoint`。它不应包含任何文件 I/O 逻辑，而是直接调用 `SpectateStateSaver` 的实例。

2.  **注册命令 (`SpectateCommand`):**
    *   创建 `SpectateCommand` 类。
    *   注册 `/cspectate points` 系列子命令 (`add`, `remove`, `edit`, `list`)。
    *   将这些命令的执行逻辑全部委托给 `SpectatePointManager`。
    *   实现命令参数的**自动补全**功能，提升用户体验。

### 步骤三：开发核心观察引擎

这是最复杂的一步，需要实现玩家的"观察"和"恢复"逻辑。

1.  **创建 `ServerSpectateManager`:**
    *   设计为**单例**，并在 Mod 初始化时传入 `MinecraftServer` 实例。
    *   创建 `PlayerSpectateSession` 内部类，用于**在内存中**临时存储一个玩家在开始观察前的**所有状态**（GameMode, Position, Rotation）。这是能够完美恢复玩家的关键。

2.  **实现核心方法 (`startSpectating` / `stopSpectating`):**
    *   **`startSpectating(player, targetPosition, distance)`:**
        1.  **保存状态:** 创建 `PlayerSpectateSession`，记录玩家的当前状态，并将其存入一个全局的 `activeSessions` Map 中。
        2.  **切换模式:** 将玩家的游戏模式设置为 `SPECTATOR`。
        3.  **启动电影镜头:** 见下方"关键概念"。
    *   **`stopSpectating(player)`:**
        1.  **恢复状态:** 从 `activeSessions` Map 中取出玩家的 `Session`，用其中保存的数据恢复玩家的游戏模式、位置和视角。
        2.  **停止镜头:** 取消与该玩家关联的定时任务。
        3.  **清理会话:** 从 `activeSessions` Map 中移除该 `Session`。

3.  **接入命令:**
    *   将 `/cspectate point <name>` 和 `/cspectate stop` 命令的逻辑接入到 `ServerSpectateManager` 的 `spectatePoint` 和 `stopSpectating` 方法。

### 步骤四：实现动态目标跟踪与循环功能

在核心引擎之上，构建更高级的功能。

1.  **扩展 `ServerSpectateManager`:**
    *   **动态目标:** 修改电影镜头任务的逻辑，使其在每次更新时，都能检查 `Session` 中是否存在一个 `targetPlayerId`。如果存在，则首先更新目标坐标为该玩家的当前位置，然后再计算相机位置。
    *   **循环逻辑:**
        1.  创建 `PlayerCycleSession` 内部类，用于在内存中跟踪玩家的循环列表进度（当前点索引，开始时间等）。
        2.  实现 `startCycle`, `stopCycle`, `nextCyclePoint` 方法。
        3.  `startCycle` 负责启动第一个点的观察，并使用 `ScheduledExecutorService` 安排一个延时任务，用于在指定时长后调用 `nextCyclePoint`。
        4.  `nextCyclePoint` 负责切换到列表的下一个点，并再次安排下一个延时任务，形成循环。

2.  **接入命令:**
    *   将 `/cspectate player`, `/cspectate coords`, 和 `/cspectate cycle` 系列命令接入 `ServerSpectateManager` 中对应的方法。

---

## 4. 关键概念与技术要点

### 状态管理：内存 vs. 硬盘
- **瞬时状态 (内存):** 玩家在观察期间的原始状态 (`PlayerSpectateSession`) 是高度动态且临时的，只在观察期间有效。将其存储在内存中（如 `ConcurrentHashMap`）是最高效和正确的选择。
- **持久化状态 (硬盘):** 观察点和玩家的循环列表配置是需要长期保存的，必须通过 `SpectateStateSaver` 写入文件系统。

### 线程安全：主线程与调度器
- **后台任务:** 为了实现平滑的镜头旋转而不卡住服务器，必须使用 `ScheduledExecutorService` 在后台线程中执行周期性任务（例如每 50ms 更新一次相机位置）。
- **主线程操作:** 所有会修改游戏世界状态的操作（如 `player.teleportTo`, `player.setGameMode`）**必须**在 Minecraft 的主服务器线程中执行。在后台任务中，需要使用 `server.execute(() -> { ... })` 将这些操作包装起来，以确保线程安全。

### 动态与静态目标统一处理
- **`player:` 前缀:** 循环列表通过一个巧妙的设计统一了静态点和动态玩家。当 `switchToCurrentCyclePoint` 方法处理一个列表项时，它会检查名称是否以 `player:` 开头。
  - **是:** 则将其解析为玩家名，进入"跟踪玩家"逻辑。
  - **否:** 则将其视为普通观察点名称，进入"观察坐标点"逻辑。
这使得循环系统具有高度的灵活性和可扩展性。 