#!/bin/bash

echo "=== 机场航站楼母婴室配套设备使用时段关联登记系统 ==="
echo ""

echo "检查端口占用情况..."
for port in 8123 8133 3349 6422; do
    if lsof -i :$port &>/dev/null; then
        echo "端口 $port 已被占用:"
        lsof -i :$port | head -5
        echo ""
    else
        echo "端口 $port 可用"
    fi
done

echo ""
echo "启动服务..."
docker compose up -d --build

echo ""
echo "等待服务启动..."
sleep 10

echo ""
echo "检查服务状态..."
docker compose ps

echo ""
echo "验证前端页面..."
curl -s http://localhost:8123/ | head -3

echo ""
echo "验证后端API..."
curl -s http://localhost:8133/api/categories/device-types

echo ""
echo ""
echo "前端访问地址: http://localhost:8123"
echo "后端API地址: http://localhost:8133"