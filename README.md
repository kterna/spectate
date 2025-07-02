# Spectate Mod

一个纯服务端的Minecraft观察模式插件，支持循环观察预设观察点。

## 特性

- 🔄 **纯服务端实现** - 客户端无需安装mod
- 📍 **观察点管理** - 添加、删除、列表管理观察点
- 🎯 **循环观察** - 自动在多个观察点间循环切换
- 💾 **数据持久化** - 所有数据自动保存，重启后保持

## 基本命令

### 观察模式
```
/cspectate player <玩家名>       # 实时跟踪指定玩家移动
/cspectate point <观察点名称>     # 观察预设观察点
/cspectate coords <x> <y> <z>    # 观察指定坐标
/cspectate stop                  # 停止观察/跟踪模式
```

### 观察点管理
```
/cspectate points add <名称> <x> <y> <z> [距离] [高度偏移] [描述]
/cspectate points edit <名称> <x> <y> <z> [距离] [高度偏移] [描述]
/cspectate points remove <名称>
/cspectate points list
```

### 循环观察
```
/cspectate cycle add point <观察点>   # 添加观察点到个人循环列表
/cspectate cycle add player <玩家>   # 添加玩家实时跟踪到循环列表
/cspectate cycle start              # 开始循环观察
/cspectate cycle stop               # 停止循环观察
/cspectate cycle duration <秒数>    # 设置每个观察点的时长
/cspectate cycle list               # 查看个人循环列表
```

## 版本要求

- Minecraft 1.21.x
- Fabric Loader >= 0.14.9

## 安装

1. 将mod文件放入服务端的 `mods` 文件夹
2. 启动服务器
3. 使用 `/cspectate` 命令开始使用

## 功能详细说明

### 实时玩家跟踪
`/cspectate player <玩家名>` 功能说明：
- **实时跟踪**: 摄像机会持续跟随目标玩家移动
- **360度旋转**: 围绕玩家以20方块距离进行360度旋转观察
- **自动高度调整**: 观察高度根据玩家位置动态调整
- **离线检测**: 目标玩家离线时自动停止跟踪
- **安全限制**: 不能跟踪自己

### 观察点编辑
使用 `edit` 子命令可以修改现有观察点的所有参数：
- **位置**: 观察点的新坐标
- **距离**: 观察时的摄像机距离（5-200方块）
- **高度偏移**: 相对于目标点的高度偏移（-50到+50方块）
- **描述**: 观察点的描述文字

### 循环玩家跟踪
`/cspectate cycle add player <玩家>` 功能说明：
- 在循环观察中添加实时玩家跟踪
- 当轮到该项时，会自动切换到跟踪指定玩家
- 实时跟随玩家移动，无需手动更新位置
- 如果目标玩家离线，会自动跳过该观察项

## 配置文件

所有数据保存在 `config/spectate_data/` 目录下：
- `spectate_points.properties` - 观察点数据
- `cycle_lists.properties` - 玩家循环列表
- `player_states.properties` - 玩家状态备份 