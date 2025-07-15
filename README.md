# Spectate Mod

一个为 Minecraft 服务器设计的旁观工具 Mod，提供强大的指令来管理和切换旁观视角，支持自定义观察点、玩家旁观以及循环旁观。

## 功能特性

本 Mod 提供了 `/cspectate` 指令及其子命令，允许玩家灵活控制旁观体验：

### 1. 观察点管理 (`/cspectate points`)

*   **`/cspectate points add <name> <pos> [distance] [heightOffset] [rotationSpeed] [description]`**
    *   在指定位置添加一个自定义观察点。`pos` 为坐标，`distance` 为摄像机与目标的水平距离，`heightOffset` 为垂直偏移，`rotationSpeed` 为环绕速度（度/秒），`description` 为可选描述。
*   **`/cspectate points remove <name>`**
    *   移除一个已保存的观察点。
*   **`/cspectate points list`**
    *   列出所有已保存的观察点名称。

### 2. 旁观指定目标

*   **`/cspectate point <name>`**
    *   旁观一个已保存的观察点。
*   **`/cspectate player <targetPlayerName>`**
    *   旁观指定玩家的第一人称视角。
*   **`/cspectate coords <x> <y> <z> [distance] [heightOffset] [rotationSpeed]`**
    *   旁观任意指定坐标。`distance`、`heightOffset` 和 `rotationSpeed` 为可选参数。

### 3. 停止旁观

*   **`/cspectate stop`**
    *   停止当前旁观会话，并将玩家恢复到旁观前的状态和位置。

### 4. 循环旁观 (`/cspectate cycle`)

*   **`/cspectate cycle add <pointName>`**
    *   将一个已保存的观察点添加到你的循环列表中。
*   **`/cspectate cycle addplayer <playerName>`**
    *   将一个玩家添加到你的循环列表中（用于循环旁观玩家）。
*   **`/cspectate cycle remove <pointNameOrPlayerName>`**
    *   从循环列表中移除一个点或玩家。
*   **`/cspectate cycle list`**
    *   列出你当前循环列表中的所有点和玩家。
*   **`/cspectate cycle clear`**
    *   清空你的循环列表。
*   **`/cspectate cycle interval <seconds>`**
    *   设置循环切换的间隔时间（秒）。
*   **`/cspectate cycle start [cinematic|follow] [mode]`**
    *   开始循环旁观列表中的点或玩家。
    *   可选择视角模式：`cinematic` 电影模式或 `follow` 跟随模式
    *   电影模式支持子模式：`slow_orbit`（慢速环绕）、`aerial_view`（高空俯瞰）、`spiral_up`（螺旋上升）、`floating`（浮游视角）
*   **`/cspectate cycle next`**
    *   手动切换到循环列表中的下一个点或玩家。

## 安装

这是一个**纯服务端 Mod**。你只需要将其安装在你的 Minecraft 服务器上。

1.  确保你的 Minecraft 服务器已安装 **Fabric Loader**。
2.  下载适用于你服务器 Minecraft 版本的 Mod `.jar` 文件。
3.  将下载的 `.jar` 文件放入服务器根目录下的 `mods` 文件夹中。
4.  启动或重启你的 Minecraft 服务器。

**注意：** 玩家客户端无需安装此 Mod。

## 使用

在游戏中，作为一名玩家，你可以在聊天框中输入 `/cspectate` 来查看所有可用的子命令和用法提示。