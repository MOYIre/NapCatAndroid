#!/bin/sh
echo "测试 Node.js 环境..."
node --version
if [ $? -eq 0 ]; then
    echo "Node.js 可以在 PRoot 环境中运行"
    node -e "console.log('当前工作目录:', process.cwd()); console.log('环境变量:', process.env.NODE_ENV || '未设置');"
else
    echo "Node.js 无法运行"
fi
