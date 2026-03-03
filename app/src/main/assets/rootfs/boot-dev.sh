#!/bin/sh
# sealdice project
export HOME=$(pwd)/root
export PATH=/bin:/sbin:/usr/bin:/usr/sbin

cd /work

if [ -z "$1" ]; then
    /bin/busybox sh
    exit 0
fi

chmod +x /root/"$1"
strace -s 5000 -f -o /work/output.txt /root/"$1" "$2" "$3" "$4" "$5" "$6" "$7" 2>&1 | tee /work/error.txt

