#!/bin/sh
export HOME=/root
export PATH=/bin:/usr/bin:/sbin:/usr/sbin

# 检查 node 是否可用
if ! command -v node >/dev/null 2>&1; then
    echo "错误: Node.js 未在环境中找到"
    echo "当前 PATH: $PATH"
    ls -la /bin/
    exit 1
fi

echo "Node.js 版本: $(node --version)"

# 如果提供了参数，则执行指定的脚本，否则启动交互式 shell
if [ $# -gt 0 ]; then
    node "$@"
else
    exec /bin/sh
fi
