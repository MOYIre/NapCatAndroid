# NapCat for Android

将 NapCatQQ 移植到 Android 平台的项目，使用户能够在 Android 设备上运行 NapCat 机器人框架。

## 项目概述

NapCat for Android 是一个将 NapCatQQ 核心功能移植到 Android 移动平台的项目。它允许用户在 Android 设备上运行基于 QQ NT 协议的机器人服务。

### 核心特性

- 在 Android 平台上运行 NapCat 机器人框架
- 后台服务持续运行，保持 QQ 账号在线
- 简洁的用户界面控制服务状态
- 电池优化友好的后台运行机制
- 支持多种 Node.js 运行方案

### 技术架构

- **Android 应用层**：使用 Java/Kotlin 编写的 Android 应用
- **PRoot 方案**：使用 PRoot 技术在 Android 上创建 Linux 环境 (类似 SealDice)
- **Termux 集成**：可选，利用 Termux 的 Node.js 环境
- **NapCat 核心**：NapCatQQ 的核心功能模块

## 项目结构

```
NapCatAndroid/
├── app/                    # Android 应用模块
│   ├── src/main/
│   │   ├── java/           # Java 源代码
│   │   │   ├── MainActivity.java     # 主界面
│   │   │   ├── NapCatService.java    # 后台服务
│   │   │   └── NodeRunner.java       # Node.js 运行器 (支持多种方案)
│   │   ├── res/            # 资源文件
│   │   └── AndroidManifest.xml
│   └── build.gradle        # 应用构建配置
├── gradle/wrapper/         # Gradle Wrapper
├── build.gradle            # 项目构建配置
├── settings.gradle         # 项目设置
├── BUILDING.md             # 构建和部署指南
├── IMPLEMENTATION.md       # 实现说明
└── README.md               # 本文件
```

## 部署方案

本项目支持多种 Node.js 运行方案：

### 方案一：PRoot (推荐)

类似 SealDice Android 的实现，使用 PRoot 技术在 Android 上运行标准 Linux 版本的 Node.js 和 NapCat：

- 优势：完全兼容标准 NapCat，无需重新编译原生模块
- 实现：部署 PRoot 二进制文件和精简 Linux 环境
- 与 SealDice Android 的实现原理相同

### 方案二：Termux 集成

利用设备上已安装的 Termux 应用及其 Node.js 环境：

- 优势：简单快捷，利用现有的成熟环境
- 要求：用户需预先安装 Termux 并配置 Node.js

### 方案三：原生移植

为 Android 平台直接编译 Node.js 和相关模块（未来考虑）

## 开发状态

### 已完成

- [x] Android 项目基础结构
- [x] 服务管理框架
- [x] UI 界面设计
- [x] 多方案 Node.js 运行时集成框架
- [x] PRoot 方案实现
- [x] Termux 集成方案
- [x] NapCat 核心功能集成框架

### 待完成

- [ ] PRoot 二进制文件和 Linux 环境部署
- [ ] NapCat 核心文件打包流程
- [ ] 完整功能测试
- [ ] 性能优化
- [ ] 安全加固

## 构建要求

### 环境要求

- Android SDK (API level 24+)
- Java JDK 8+
- Node.js (用于构建 NapCat)

### 构建步骤

1. 设置 Android SDK 环境变量
2. 准备 NapCat 核心文件
3. 构建 Android 应用

详细构建步骤请参阅 [BUILDING.md](BUILDING.md)。

## 部署说明

部署过程主要涉及：

1. **PRoot 环境**：PRoot 二进制文件和 Linux 根文件系统
2. **NapCat 核心文件**：打包 NapCat 的 JS 代码和依赖
3. **启动脚本**：自动选择最优运行方案

具体部署方案请参阅 [IMPLEMENTATION.md](IMPLEMENTATION.md)。

## 使用说明

1. 安装 APK 到 Android 设备
2. 启动应用并授予必要权限
3. 点击"Start Service"启动 NapCat 服务
4. 服务将在后台持续运行

## 贡献

欢迎提交 Issue 和 Pull Request 来改进项目。

## 许可证

本项目遵循原始 NapCatQQ 项目的许可证协议。

## 免责声明

本项目仅供学习和研究使用，请遵守相关法律法规，不要用于任何违法用途。

## 相关项目

- [NapCatQQ](https://github.com/NapNeko/NapCatQQ) - 原始 NapCatQQ 项目
- [sealdice-android](https://github.com/sealdice/sealdice-android) - 本项目的灵感来源，PRoot 方案原理参考