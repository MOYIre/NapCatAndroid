#!/bin/sh
# NapCat for Android boot script
# Based on the SealDice implementation approach

export HOME=/root
export PATH=/bin:/sbin:/usr/bin:/usr/sbin

cd /work

if [ -z "$1" ]; then
    /bin/busybox sh
    exit 0
fi

# Make the NapCat binary executable
chmod +x /root/"$1"

# Set up Node.js environment if needed
export NODE_PATH=/usr/lib/node_modules
export NAPCAT_HOME=/root

# Run NapCat
/root/"$1"