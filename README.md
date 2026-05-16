# 足迹日记 (LifeTrack)

> 完全本地运行的人生时空轨迹记录App，零云端零泄露

<p align="center">
  <img src="https://img.shields.io/badge/version-1.0.0-blue" alt="version">
  <img src="https://img.shields.io/badge/platform-Android-green" alt="platform">
  <img src="https://img.shields.io/badge/license-GPLv3-orange" alt="license">
</p>

## 截图

| 首页 | 地图 | 日历 | 统计 |
|:---:|:---:|:---:|:---:|
| 🏠 | 🗺️ | 📅 | 📊 |

*（截图待补充）*

## 核心特性

- 🔒 **纯本地**：所有数据只存在你的手机里，无服务器、无账号、无云端同步
- 📍 **开机即录**：后台GPS持续追踪，开机自启动，零干预
- 🗺️ **实时位置**：地图显示当前位置+轨迹线，支持高德瓦片源
- 📊 **时空快照**：每日轨迹地图 + 停留时间轴 + 行程时间表
- 🔋 **省电设计**：自适应采样频率（静止60s/步行10s/乘车5s）
- 🌙 **暗色主题**：Material Design 3 动态配色

## 已实现功能 (v1.0.0)

- ✅ 后台GPS定位服务（前台Service保活）
- ✅ 实时地图显示当前位置（高德瓦片源）
- ✅ Room本地数据库（4表：LocationPoint/TripSegment/StayPoint/DailyStats）
- ✅ 停留点检测算法
- ✅ 5页导航：首页/地图/日历/统计/设置
- ✅ 设置页（采样频率/备份恢复/关于）
- ✅ 开机自启动

## 待完善功能

- 📋 轨迹线绘制（地图上画出运动轨迹）
- 📋 日历页功能完善
- 📋 统计页图表（MPAndroidChart）
- 📋 数据导出（GPX/GeoJSON）
- 📋 离线地图瓦片下载

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| 平台 | Android (minSdk 26, targetSdk 34) |
| 架构 | MVVM + Repository |
| 数据库 | Room (SQLite) |
| 地图 | OSMDroid 6.1.20 + 高德瓦片源 |
| 依赖注入 | Hilt |
| 定位 | FusedLocationProvider |
| 图表 | MPAndroidChart |
| 导航 | Navigation Component |

## 下载安装

### 方式一：GitHub Release（推荐）

1. 前往 [Releases](https://github.com/Elephenman/lifetrack/releases) 下载最新APK
2. 传输到手机并安装
3. 安装后授予以下权限：
   - 📍 定位权限 → 后台定位权限
   - 🔋 电池优化白名单
   - 🚀 自启动权限（小米/华为等需手动开启）

### 方式二：自行构建

```bash
git clone https://github.com/Elephenman/lifetrack.git
cd lifetrack
./gradlew assembleDebug
# APK输出: app/build/outputs/apk/debug/app-debug.apk
```

## 权限说明

| 权限 | 用途 | 是否必须 |
|------|------|---------|
| ACCESS_FINE_LOCATION | GPS精确定位 | ✅ |
| ACCESS_COARSE_LOCATION | 网络粗略定位 | ✅ |
| ACCESS_BACKGROUND_LOCATION | 后台持续定位 | ✅ |
| FOREGROUND_SERVICE | 前台服务保活 | ✅ |
| RECEIVE_BOOT_COMPLETED | 开机自启动 | 可选 |
| POST_NOTIFICATIONS | 前台服务通知 | Android 13+ |

**所有数据100%存储在本地，绝不会上传任何服务器。**

## 项目结构

```
app/src/main/java/com/elephenman/lifetrack/
├── LifetrackApplication.kt    # Application + Hilt入口
├── data/
│   ├── db/                    # Room数据库 + 4个Entity + DAO
│   ├── model/                 # 数据模型
│   └── repository/            # Repository层
├── service/
│   └── LocationService.kt     # 核心后台定位Service
├── ui/
│   ├── home/                  # 首页Fragment
│   ├── map/                   # 地图Fragment (OSMDroid)
│   ├── calendar/              # 日历Fragment
│   ├── stats/                 # 统计Fragment
│   └── settings/              # 设置Fragment
└── util/
    └── StayPointDetector.kt   # 停留点检测算法
```

## 开源许可

[GPLv3](LICENSE)

---

> 💡 灵感来源：GPSLogger的隐私 + Google Timeline的可视化，纯本地，你的轨迹只属于你。
