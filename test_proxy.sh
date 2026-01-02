#!/bin/bash

# CellularRouter 测试脚本
# 用于测试SOCKS5和HTTP代理功能

echo "========================================"
echo "   CellularRouter 代理测试脚本"
echo "========================================"
echo ""

# 检查参数
if [ $# -lt 2 ]; then
    echo "用法: $0 <设备IP> <端口>"
    echo "示例: $0 192.168.1.100 1080"
    exit 1
fi

DEVICE_IP=$1
PORT=$2

echo "设备IP: $DEVICE_IP"
echo "端口: $PORT"
echo ""

# 测试1: SOCKS5代理
echo "========================================"
echo "测试 1: SOCKS5 代理"
echo "========================================"
echo "执行: curl --socks5 $DEVICE_IP:$PORT https://ifconfig.me"
echo ""

SOCKS5_RESULT=$(curl --socks5 "$DEVICE_IP:$PORT" https://ifconfig.me 2>&1)
SOCKS5_EXIT_CODE=$?

if [ $SOCKS5_EXIT_CODE -eq 0 ]; then
    echo "✅ SOCKS5代理测试成功"
    echo "返回的IP地址: $SOCKS5_RESULT"
else
    echo "❌ SOCKS5代理测试失败"
    echo "错误信息: $SOCKS5_RESULT"
fi
echo ""

# 测试2: HTTP代理
echo "========================================"
echo "测试 2: HTTP 代理"
echo "========================================"
echo "执行: curl --proxy http://$DEVICE_IP:$PORT https://ifconfig.me"
echo ""

HTTP_RESULT=$(curl --proxy "http://$DEVICE_IP:$PORT" https://ifconfig.me 2>&1)
HTTP_EXIT_CODE=$?

if [ $HTTP_EXIT_CODE -eq 0 ]; then
    echo "✅ HTTP代理测试成功"
    echo "返回的IP地址: $HTTP_RESULT"
else
    echo "❌ HTTP代理测试失败"
    echo "错误信息: $HTTP_RESULT"
fi
echo ""

# 测试3: 检查IP是否相同
echo "========================================"
echo "测试 3: 验证结果"
echo "========================================"

if [ $SOCKS5_EXIT_CODE -eq 0 ] && [ $HTTP_EXIT_CODE -eq 0 ]; then
    if [ "$SOCKS5_RESULT" = "$HTTP_RESULT" ]; then
        echo "✅ SOCKS5和HTTP代理返回相同的IP"
        echo "蜂窝网络IP: $SOCKS5_RESULT"
    else
        echo "⚠️  SOCKS5和HTTP代理返回不同的IP"
        echo "SOCKS5 IP: $SOCKS5_RESULT"
        echo "HTTP IP: $HTTP_RESULT"
    fi
else
    echo "❌ 无法完成验证 - 部分测试失败"
fi
echo ""

# 总结
echo "========================================"
echo "   测试总结"
echo "========================================"

TOTAL_TESTS=2
PASSED_TESTS=0

if [ $SOCKS5_EXIT_CODE -eq 0 ]; then
    ((PASSED_TESTS++))
fi

if [ $HTTP_EXIT_CODE -eq 0 ]; then
    ((PASSED_TESTS++))
fi

echo "通过: $PASSED_TESTS/$TOTAL_TESTS"

if [ $PASSED_TESTS -eq $TOTAL_TESTS ]; then
    echo "状态: ✅ 所有测试通过"
    exit 0
else
    echo "状态: ❌ 部分测试失败"
    exit 1
fi
