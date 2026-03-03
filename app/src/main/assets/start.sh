#!/bin/sh
# sealdice project

DIR=$(realpath $(dirname "$0"))

unset LD_PRELOAD
export LD_LIBRARY_PATH=$DIR/proot:$LD_LIBRARY_PATH
export PROOT_TMP_DIR=$DIR/proot/tmp
export PROOT_LOADER=$DIR/proot/loader
export DOTNET_GCHeapHardLimit=1C0000000

EXE=$1
if [ -z "$EXE" ]; then
    echo "必须指定执行参数，如 ./start.sh ./app/Lagrange.OneBot"
    exit 1
fi

if [ "$1" != "shell" ]; then
    EXE_PATH=$(realpath $(dirname "$1"))
    EXE_FN=$(basename "$1")
else
    EXE_PATH=$(pwd)
fi

$DIR/proot/proot -r $DIR/rootfs -w / -b /proc -b /dev -b /sys -b /data -b /sdcard -b $(pwd):/work -b $EXE_PATH:/root /bin/busybox sh /boot.sh $EXE_FN $2 $3 $4 $5 $6 $7

