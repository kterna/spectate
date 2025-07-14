# 更新日志

## 1.0.5
- **新增功能**
  - 新增 `/cspectate config` 命令系统，支持动态配置管理
  - 支持配置重载：`/cspectate config reload`
  - 支持配置查看：`/cspectate config get <path>`
  - 支持配置修改：`/cspectate config set <path> <value>`
  - 支持配置列表：`/cspectate config list [category]`

- **改进**
  - 重构配置结构，将 `defaults` 重命名为更直观的 `settings`
  - 将 `messages` 重命名为 `lang` 以提高语义清晰度
  - 为所有配置参数添加中文注释，提升可读性

## 1.0.4
- **新增功能**
  - 全新电影视角系统，支持4种专业视角模式：慢速环绕、高空俯瞰、螺旋上升、浮游视角
  - 基于物理模拟的浮游摄像机，具备多层噪声生成和智能跟踪功能
  - 新增电影视角命令：`/cspectate player <name> cinematic <mode>` 和 `/cspectate point <name> cinematic <mode>`
  - 增加8个浮游视角配置参数，支持自定义物理效果

- **改进**
  - 重构SpectateSession类，支持多种视角模式
  - 增强命令系统，新增Tab补全支持
  - 添加视角模式提示消息和完整中文本地化
  - 优化性能，使用噪声表缓存和50ms更新频率

- **修复**
  - 修复switch语句中的变量作用域问题
  - 修复Session变量访问错误
  - 添加缺失的本地化消息键

## 1.0.3
- **修复**
  - 修复维度切换问题，确保玩家在维度间移动时旁观状态正确处理
  - 修复旁观玩家相关问题，提升旁观体验稳定性

- **优化**
  - 更新 Mixin 配置路径为 'spectate.mixins.json'，提高配置管理效率
  - 优化 SpectateStateSaver 错误处理机制，使用日志记录器替代标准错误输出
  - 增强 CycleService 循环点管理逻辑，确保移除循环点时正确处理任务状态
  - 更新 fabric.mod.json 限制运行环境为服务器端，提升安全性
  - 整体提升代码可读性和稳定性

## 1.0.2
- **优化**
  - 引入灵活的配置文件系统 (`config.json`)，允许自定义所有消息和默认参数。
  - 将数据存储从 `.properties` 文件迁移到更健壮的 JSON 格式，提高了数据安全性和可扩展性。
  - 重构命令自动补全逻辑，使用 `SuggestionProvider` 提高代码的可维护性。

## 1.0.1
- 修复玩家重进游戏后旁观状态未正确恢复的问题。

## 1.0.0
- 初次发布。
