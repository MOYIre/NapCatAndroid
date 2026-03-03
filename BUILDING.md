# NapCat for Android - 构建和部署指南

## 概述

本文档描述了如何构建和部署 NapCat for Android 应用。由于 NapCat 依赖于 Node.js 运行时和原生库，部署过程比普通 Android 应用更复杂。

## 构建要求

### 环境要求
- Android SDK
- Android NDK (用于编译原生库，如果使用原生方案)
- Node.js (用于构建 NapCat)
- Java JDK 8 或更高版本
- (可选) PRoot 二进制文件和 Linux 根文件系统

### 系统环境变量
- `ANDROID_HOME` 或 `ANDROID_SDK_ROOT`：指向 Android SDK 目录
- `ANDROID_NDK_ROOT`：指向 Android NDK 目录 (如果使用原生方案)

## 构建步骤

### 1. 准备 NapCat 核心文件

在部署到 Android 之前，需要准备以下文件：

1. **NapCat 源代码**：从 NapCatQQ 项目构建的文件
2. **依赖包**：NapCat 所需的 Node.js 包

```bash
# 示例：准备 NapCat 文件
mkdir -p app/src/main/assets/napcat
cp -r /path/to/napcat/dist/* app/src/main/assets/napcat/
```

### 2. (原生方案) 编译原生库

如果采用原生 Node.js 移植方案，NapCat 可能需要一些原生库，这些库需要为 Android 平台编译：

```bash
# 示例：编译原生库 (使用 NDK)
ndk-build -C jni
```

### 3. (PRoot 方案) 准备 PRoot 环境

如果采用 PRoot 方案，则需要准备：

1. **PRoot 二进制文件**：为 Android 平台编译的 PRoot
2. **Linux 根文件系统**：精简的 Linux 环境
3. **Node.js**：Linux 版本的 Node.js (在 PRoot 环境中运行)

```bash
# 示例：准备 PRoot 环境
mkdir -p app/src/main/assets/proot-rootfs
# 部署 PRoot 二进制文件和基础 Linux 环境
```

### 4. 构建 Android 项目

使用 Gradle 构建 Android 项目：

```bash
# 生成调试版本
./gradlew assembleDebug

# 生成发布版本
./gradlew assembleRelease
```

## 部署方案

### 方案一：PRoot 方案 (推荐，类似 SealDice)

使用 PRoot 技术在 Android 上创建一个 Linux 环境：

1. 集成 PRoot 二进制文件到 APK
2. 部署精简 Linux 根文件系统
3. 在 PRoot 环境中安装 Linux 版本的 Node.js
4. 将 NapCat 部署到 PRoot 环境中运行

### 方案二：Termux 集成

在应用内集成 Termux 环境：

1. 应用启动时检查并安装 Termux 环境
2. 在 Termux 环境中运行 NapCat
3. 通过 AIDL 或其他 IPC 机制与主应用通信

### 方案三：Node.js Mobile

使用 Node.js Mobile 项目，这是一个专门为移动平台设计的 Node.js 分发版本：

1. 下载 Node.js Mobile Android SDK
2. 将 NapCat 代码集成到 Node.js Mobile 模板中
3. 使用提供的构建工具生成 APK

### 方案四：自定义 Node.js 移植

为 Android 平台从源码编译 Node.js：

1. 使用 Android NDK 编译 Node.js
2. 交叉编译所有原生依赖
3. 打包到 APK 的 native 库目录

## 文件结构

```
app/
├── src/
│   └── main/
│       ├── java/com/napcat/android/
│       │   ├── MainActivity.java      # 主界面
│       │   ├── NapCatService.java     # 后台服务
│       │   └── NodeRunner.java        # Node.js 运行器
│       ├── assets/                    # 应用资源
│       │   ├── napcat/                # NapCat 核心文件 (PRoot 方案)
│       │   │   ├── package.json
│       │   │   ├── napcat.js          # 入口文件
│       │   │   ├── node_modules/      # 依赖
│       │   │   └── config/            # 配置文件
│       │   └── proot-rootfs/          # PRoot Linux 环境 (PRoot 方案)
│       │       ├── bin/
│       │       ├── usr/
│       │       ├── lib/
│       │       └── ...                # Linux 根文件系统
│       ├── jniLibs/                   # 原生库 (原生方案)
│       │   ├── arm64-v8a/
│       │   │   └── libnode.so         # Node.js 运行时
│       │   └── armeabi-v7a/
│       │       └── libnode.so         # Node.js 运行时
│       ├── res/                       # 资源文件
│       └── AndroidManifest.xml        # 应用配置
├── build.gradle                       # 项目构建配置
└── ...
```

## 权限说明

NapCat Android 应用需要以下权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

## 后台服务管理

NapCat 作为一个聊天服务需要长时间运行，使用 Android 的前台服务：

1. 服务启动时创建前台通知
2. 实现服务崩溃自动重启机制
3. 处理 Android 电池优化策略

## PRoot 方案详细说明

### PRoot 环境部署

PRoot 方案需要将完整的 Linux 环境打包到 APK 中：

```bash
# PRoot 环境结构示例
app/src/main/assets/proot-rootfs/
├── bin/
│   ├── sh
│   ├── node -> /usr/bin/node
│   └── ...
├── usr/
│   ├── bin/
│   │   ├── node
│   │   ├── npm
│   │   └── ...
│   └── lib/
│       └── ...
├── lib/
│   ├── libdl.so.2
│   ├── libc.so.6
│   └── ...
└── etc/
    └── ...
```

### 启动命令示例

```bash
# 使用 PRoot 运行 NapCat
./proot -R proot-rootfs -b /data/data/com.napcat.android/files/napcat:/home/napcat -b /dev -b /proc -b /sys -w /home/napcat /usr/bin/node /home/napcat/index.js
```

## 测试步骤

1. **单元测试**：测试 Java 代码部分
2. **集成测试**：测试 PRoot 或 Node.js 运行时集成
3. **功能测试**：测试 NapCat 核心功能
4. **性能测试**：测试内存和电池使用

## 发布准备

### 签名配置

为发布版本配置应用签名：

```gradle
android {
    signingConfigs {
        release {
            storeFile file('path/to/keystore.jks')
            storePassword 'keystore_password'
            keyAlias 'key_alias'
            keyPassword 'key_password'
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

### Proguard 配置

添加 Proguard 规则以保护必要的类和方法：

```proguard
-keep class com.napcat.android.** { *; }
-keep class org.nodejs.** { *; }
```

## 部署到设备

```bash
# 安装到连接的设备
adb install app/build/outputs/apk/release/app-release.apk

# 或通过 ADB 推送并安装
adb push app-release.apk /sdcard/
adb shell pm install /sdcard/app-release.apk
```

## 故障排除

### 常见问题

1. **PRoot 启动失败**：确认 PRoot 二进制文件已正确打包且具有执行权限
2. **Linux 环境问题**：检查根文件系统是否完整且兼容
3. **权限问题**：检查 AndroidManifest.xml 中是否包含必要的权限
4. **服务被系统杀死**：检查应用的电池优化设置

### 调试

```bash
# 查看应用日志
adb logcat | grep -i napcat

# 查看服务状态
adb shell dumpsys activity services com.napcat.android.NapCatService
```

## 未来工作

1. **优化 PRoot 启动时间**：减少 Linux 环境启动时间
2. **减小 APK 大小**：优化 Linux 根文件系统大小
3. **增强稳定性**：改进错误处理和恢复机制
4. **支持更多架构**：添加 x86 和 x86_64 架构支持
5. **实现自动更新**：添加 NapCat 核心文件的更新机制