# PRoot 二进制文件获取指南

## 获取 PRoot 二进制文件

PRoot 需要为 Android 平台编译的二进制文件。以下是获取方式：

### 方法一：从预编译版本获取

1. 从 PRoot 的 GitHub 发布页面下载适用于 Android 的预编译二进制文件
2. 确保获取的版本支持你的目标 CPU 架构（arm64-v8a, armeabi-v7a 等）

### 方法二：自行编译

如果需要自行编译 PRoot，可以使用以下命令：

```bash
# 在 Termux 中安装编译工具
pkg install git make gcc

# 克隆 PRoot 源码
git clone https://github.com/proot-me/proot.git
cd proot

# 编译 PRoot
make -C src
```

### 方法三：使用现有项目

参考其他 Android 项目（如 Termux）中的 PRoot 实现：

```bash
# 例如，从 Termux 的包仓库获取
pkg install proot
```

## CPU 架构支持

为确保兼容性，需要为以下架构提供 PRoot 二进制文件：

- `arm64-v8a` - ARM 64位 (主流 Android 设备)
- `armeabi-v7a` - ARM 32位 (较老的 Android 设备)
- `x86` - 32位 x86 (模拟器或少数设备)
- `x86_64` - 64位 x86 (模拟器或少数设备)

## 文件放置

在 Android 项目的 `src/main/assets` 目录下放置 PRoot 二进制文件：

```
app/src/main/assets/
├── proot          # ARM 64位版本 (或通用版本)
├── proot-armeabi-v7a  # ARM 32位版本 (可选)
└── proot-x86      # x86 版本 (可选)
```

在实际部署时，应用会根据设备架构选择合适的 PRoot 二进制文件。

## 权限要求

PRoot 二进制文件需要可执行权限，部署时会自动设置。

## 注意事项

1. PRoot 的性能可能不如原生执行，但对于运行标准 Linux 应用（如 Node.js）是足够的
2. 某些需要系统级权限的操作可能无法在 PRoot 环境中正常工作
3. 确保 PRoot 版本与目标 Linux 环境兼容