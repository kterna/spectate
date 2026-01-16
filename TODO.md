# Spectate Mod 待实现功能

## 观察点分组/标签
- `/cspectate points add pvp_arena group:pvp` 添加观察点时指定分组
- `/cspectate points list pvp` 只显示指定分组的观察点
- 方便管理大量观察点

## 命令别名/快捷命令 ✅
- `/cs` 作为 `/cspectate` 的短别名
- 子命令缩写设计（避免歧义）：
  - `/cs p <player>` = player ✅
  - `/cs pt <name>` = point ✅
  - `/cs pts` = points ✅
  - `/cs co <x y z>` = coords ✅
  - `/cs cy` = cycle ✅
  - `/cs s` = stop ✅
  - `/cs w` = who ✅

## 旁观者状态查询 ✅
- `/cspectate who` 显示当前服务器旁观状态 ✅
- 列出：谁正在旁观、旁观的目标是什么 ✅
- 便于管理员了解全局旁观情况 ✅

## 观看时长统计与排行榜
- 记录每个玩家的旁观时长
- 记录每个玩家被旁观的时长
- `/cspectate stats` 查看自己的观看统计
- `/cspectate stats <player>` 查看指定玩家
- `/cspectate top` 排行榜（谁看得最多/谁最受欢迎）

## ActionBar信息显示
- 旁观时在ActionBar持续显示目标信息
- 内容：玩家名、生命值、坐标、维度、倒计时
- 可配置显示哪些信息

## 记住上次视角模式
- 保存玩家上次使用的视角模式
- 下次旁观时默认使用该模式
- 不用每次都手动指定

# TODO
- 观察点分组/标签 []
- 命令别名/快捷命令 [x]
- 旁观者状态查询 [x]
- 观看时长统计与排行榜 []
- ActionBar信息显示 []
- 记住上次视角模式 []