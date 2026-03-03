#!/bin/sh
# NapCat for Android 启动脚本

DIR=$(realpath $(dirname "$0"))

unset LD_PRELOAD
export LD_LIBRARY_PATH=$DIR/proot:$LD_LIBRARY_PATH
export PROOT_TMP_DIR=$DIR/proot/tmp
export PROOT_LOADER=$DIR/proot/loader

# 检查必要的文件
if [ ! -f "$DIR/rootfs/bin/node" ]; then
    echo "错误: 未找到 Node.js 二进制文件"
    exit 1
fi

if [ ! -f "$DIR/napcat.js" ]; then
    echo "错误: 未找到 NapCat 入口文件"
    exit 1
fi

# 启动 NapCat
echo "启动 NapCat for Android..."

# 使用 PRoot 运行 NapCat
$DIR/proot/proot -r $DIR/rootfs -w / -b /proc -b /dev -b /sys -b /data -b /sdcard -b $(pwd):/work -b $DIR:/app /bin/busybox sh /boot.sh /app/napcat.js
