# NapCat for Android 完整构建指南

## 环境要求

### 本地构建（推荐）
- Java JDK 8 或更高版本
- Android SDK (包含 build-tools, platform-tools)
- Android API 级别 24 或更高
- Gradle 7.0 或更高版本

### 可选：使用 Android Studio
- 下载并安装 Android Studio
- 通过 Android Studio 安装 Android SDK

## 借鉴 SealDice 的实现方式

通过分析 SealDice Android APK，我们了解到其使用 PRoot 技术来运行 Go 编译的原生应用：

1. **PRoot 二进制文件**: 包含 proot、loader、libtalloc 等文件
2. **Linux 根文件系统**: 包含 busybox 和必要的 Linux 工具
3. **启动脚本**: start.sh 和 boot.sh 脚本协调 PRoot 环境的启动
4. **核心应用**: 编译后的二进制文件（如 sealdice-core）

## PRoot 二进制文件准备

从 SealDice 实现参考，您需要准备以下 PRoot 组件：

1. 创建 PRoot 目录结构：
   ```
   assets/proot/
   ├── proot          # PRoot 二进制文件
   ├── loader         # PRoot 加载器
   ├── libtalloc.so.2 # 依赖库
   └── tmp/           # 临时目录
   ```

2. 获取 PRoot 二进制文件的方法：
   ```bash
   # 方法1: 从 proot-me 获取
   # 参考: https://github.com/proot-me/proot-distro
   
   # 方法2: 使用 Termux 获取
   pkg install proot
   ```

## Linux 根文件系统（rootfs）

创建包含 Node.js 的最小 Linux 环境：

1. 使用 proot-distro (如果已安装)：
   ```bash
   proot-distro install ubuntu
   # 然后安装 Node.js
   proot-distro login ubuntu --shared-tmp -- env LC_ALL=C.UTF-8 bash -c "apt update && apt install -y nodejs npm"
   ```

2. 或从 SealDice 的 rootfs 结构参考：
   ```
   assets/rootfs/
   ├── bin/           # busybox 和系统命令
   ├── lib/           # 系统库
   ├── usr/           # 用户程序目录
   │   └── bin/       # 包含 nodejs 等工具
   ├── etc/           # 配置文件
   ├── home/          # 用户目录
   └── opt/           # 应用程序目录
   ```

## NapCat 核心文件

从 NapCatQQ 项目构建核心文件：

1. 克隆 NapCatQQ 仓库：
   ```bash
   cd /path/to/andNapCat
   cd NapCatQQ
   ```

2. 安装依赖并构建：
   ```bash
   npm install
   # 可能需要构建 NapCat 核心
   npm run build:shell
   ```

3. 将构建文件复制到 Android 项目的 assets 目录：
   ```
   NapCatAndroid/app/src/main/assets/napcat/
   ```

## 启动脚本

项目已包含基于 SealDice 实现的启动脚本：
- `assets/start.sh` - 主启动脚本
- `assets/boot.sh` - PRoot 环境中的启动脚本

## 构建 APK

### 方法一：使用 Gradle 命令行

1. 进入项目目录：
   ```bash
   cd NapCatAndroid
   ```

2. 构建调试版本：
   ```bash
   ./gradlew assembleDebug
   ```

3. 构建发布版本：
   ```bash
   ./gradlew assembleRelease
   ```

生成的 APK 文件将位于：
- 调试版本：`app/build/outputs/apk/debug/app-debug.apk`
- 发布版本：`app/build/outputs/apk/release/app-release.apk`

### 方法二：使用 Android Studio

1. 启动 Android Studio
2. 选择 "Open an existing project"
3. 选择 `/path/to/andNapCat/NapCatAndroid` 目录
4. 等待项目同步完成
5. 点击 "Build" -> "Build Bundle(s) / APK(s)" -> "Build APK(s)"

## 部署到设备

1. 启用设备的 USB 调试模式
2. 连接设备到电脑
3. 使用 adb 安装：
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## 故障排除

### 构建错误
- 确保 Android SDK 路径已正确设置
- 检查 JAVA_HOME 环境变量
- 确保所有依赖文件都已正确放置

### 运行时错误
- 检查 PRoot 二进制文件是否具有执行权限
- 验证 Linux 根文件系统是否完整
- 确认 NapCat 核心文件是否正确部署

## 重要说明

当前的 NapCatAndroid 项目是一个框架实现，展示了如何在 Android 上使用 PRoot 技术运行需要 Linux 环境的 Node.js 应用。要获得一个完全功能的 APK，还需要：

1. **PRoot 二进制文件**: 从 proot-me 获取或构建
2. **Node.js rootfs**: 包含 Node.js 运行时的 Linux 环境
3. **NapCat 核心文件**: 完整的 NapCatQQ 核心文件
4. **所有原生依赖库**: Android 版本

这个实现基于 SealDice Android 的类似原理，使用 PRoot 在 Android 上创建一个 Linux 环境来运行标准的 Node.js 应用，与 SealDice 运行 Go 应用的方式相似。