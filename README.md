# 机场航站楼母婴室配套设备使用时段关联登记系统

## 启动方式

### Docker Compose（推荐）

```bash
docker compose up -d --build
```

### 前端地址

http://localhost:8123

### 后端API地址

http://localhost:8133

## 环境变量

端口配置见 `.env` 文件：

- `FRONTEND_PORT=8123` - 前端端口
- `SERVER_PORT=8133` - 后端端口
- `MYSQL_PORT=3349` - MySQL端口
- `REDIS_PORT=6422` - Redis端口

## 目录结构

- `backend/` - Spring Boot 后端
- `frontend/` - Vue3 + Vite 前端
- `docker-compose.yml` - Docker Compose 配置
- `.env` - 环境变量
- `start.sh` - 启动脚本