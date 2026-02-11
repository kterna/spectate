# Spectate Mod

一个为 Minecraft 服务器设计的旁观工具 Mod，提供强大的指令来管理和切换旁观视角，支持自定义观察点、玩家旁观以及循环旁观。

## 功能特性

本 Mod 提供了 `/cspectate`（或 `/cs`）指令及其子命令，允许玩家灵活控制旁观体验：

### 1. 观察点管理 (`/cspectate points`)

*   **`/cs pts add <name> <pos> [description] [group:<groupName>]`**
    *   在指定位置添加一个自定义观察点。`pos` 为坐标，`description` 为可选描述。
    *   支持在描述中指定分组（例如 `group:pvp`），便于分类管理。
*   **`/cs pts remove <name>`**
    *   移除一个已保存的观察点。
*   **`/cs pts list [group]`**
    *   列出已保存的观察点。可指定 `group` 参数筛选特定分组的观察点。

### 2. 旁观指定目标

*   **`/cs p <targetPlayer> [mode]`**
    *   旁观指定玩家。`[mode]` 可选：
        *   `follow` (跟随)
        *   `slow_orbit` (慢速环绕)
        *   `aerial_view` (高空俯瞰)
        *   `spiral_up` (螺旋上升)
        *   `floating` (浮游视角)
        *   `orbit` (默认环绕)
*   **`/cs pt <name> [mode]`**
    *   旁观一个已保存的观察点。支持上述 `[mode]` 选项。
*   **`/cs co <x> <y> <z> [distance] [heightOffset] [rotationSpeed]`**
    *   旁观任意指定坐标。`distance`、`heightOffset` 和 `rotationSpeed` 为可选参数。

### 3. 停止旁观

*   **`/cs stop`**
    *   停止当前旁观会话，并将玩家恢复到旁观前的状态和位置。

### 4. 循环旁观 (`/cspectate cycle`)

*   **`/cs cy add <pointName>`**
    *   将一个已保存的观察点添加到你的循环列表中。
*   **`/cs cy addgroup <groupName>`**
    *   将指定分组下的所有观察点一键添加到你的循环列表中。
*   **`/cs cy addplayer <playerName>`**
    *   将一个玩家添加到你的循环列表中（用于循环旁观玩家）。
*   **`/cs cy addplayerall`**
    *   自动将所有在线玩家添加到循环列表，并自动添加未来加入的玩家。
    *   支持 `prefix <前缀>` 和 `suffix <后缀>` 参数来排除特定名称的玩家（如假人）。
*   **`/cs cy remove <pointNameOrPlayerName>`**
    *   从循环列表中移除一个点或玩家。
*   **`/cs cy list`**
    *   列出你当前循环列表中的所有点和玩家。
*   **`/cs cy clear`**
    *   清空你的循环列表。
*   **`/cs cy interval <seconds>`**
    *   设置循环切换的间隔时间（秒）。
*   **`/cs cy start [mode]`**
    *   开始循环旁观列表中的点或玩家。
    *   `[mode]` 参数支持所有电影视角和跟随模式。
    *   Action Bar 将显示剩余切换时间的倒计时。
*   **`/cs cy next`**
    *   手动切换到循环列表中的下一个点或玩家。

### 5. 状态与统计 (`/cspectate`)

*   **`/cs who`**
    *   查看当前服务器上所有正在旁观的玩家及其目标。
*   **`/cs stats [player]`**
    *   查看自己或指定玩家的观看时长统计（观看时长和被观看时长）。
*   **`/cs top [viewing|watched]`**
    *   查看观看时长 (`viewing`) 或被观看时长 (`watched`) 的排行榜。

### 6. 其他特性

*   **Action Bar 信息显示**：旁观时会在屏幕下方持续显示目标玩家的信息（生命值、坐标）或观察点描述，以及循环模式下的倒计时。
*   **智能记忆模式**：Mod 会自动记住你上次使用的旁观模式（如电影模式或浮游视角），下次旁观时无需重复输入模式参数。
*   **配置管理 (`/cs config`)**：支持热重载和在线修改配置参数，安装客户端模组可在配置页面直接修改。
*   **客户端优化**：本mod只在服务端安装即可使用，客户端同样安装可以支持更平滑的视角。
*   **支持移轴滤镜**：移轴滤镜为实验性功能，谨慎使用。

## 使用

在游戏中，作为一名玩家，你可以在聊天框中输入 `/cspectate` (或别名 `/cs`) 来查看所有可用的子命令和用法提示。
