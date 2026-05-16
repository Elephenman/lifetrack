# 足迹日记 (LifeTrack)

> 完全本地运行的人生时空轨迹记录App，零云端零泄露

## 核心特性

- 🔒 **纯本地**：所有数据只存在你的手机里，无服务器、无账号、无云端同步
- 📍 **开机即录**：后台GPS持续追踪，开机自启动，零干预
- 🗺️ **离线地图**：OSMDroid离线瓦片，无需联网也能查看轨迹
- 📊 **时空快照**：每日自动生成轨迹地图 + 停留时间轴 + 行程时间表
- 🔋 **省电设计**：自适应采样频率（静止60s/步行10s/乘车5s）

## 技术栈

- Kotlin + Android原生
- Room (SQLite) 本地数据库
- OSMDroid 离线地图
- Hilt 依赖注入
- FusedLocationProvider 后台定位
- MPAndroidChart 统计图表

## 构建

```bash
./gradlew assembleDebug
```

## 安装

1. 从 [GitHub Releases](https://github.com/Elephenman/lifetrack/releases) 下载APK
2. 安装后授予：定位权限 → 后台定位权限 → 自启动权限 → 电池优化白名单

## 开源许可

GPLv3
