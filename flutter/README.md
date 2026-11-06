# Marco Movie · 跨平台客户端（Windows / macOS / iPhone / iPad）

本目录为 Marco Movie 的 Flutter 跨平台工程，覆盖 **Windows 桌面端**、**Apple macOS（Mac 电脑）**、**iOS（iPhone）** 与 **iPadOS（iPad）**，并与安卓端（手机 + 平板 + 电视三合一 APK）完全互通配置、收藏与观看历史备份。

> **免责声明**：本产品仅为本地与网络流媒体播放工具，**不内置、不提供、不托管**任何影视资源、直播源或配置地址。用户需自行合法获取配置与内容访问权限。

## 平台适配特性

| 平台 | 界面形态 | 交互适配 |
|---|---|---|
| **Windows / macOS** | 桌面大屏毛玻璃侧边导航栏（`NavigationRail`）+ 4~7 列自适应海报墙 + 左右分栏详情页 | 键盘快捷键（空格播放/暂停、`←`/`→` 快进快退 15 秒、`↑`/`↓` 调节音量、`Esc` 返回）、鼠标滚轮与悬停高亮 |
| **iPad（iPadOS）** | 横竖屏自适应：横屏侧边导航栏 + 双栏详情；竖屏底部导航栏 + 3~4 列网格 | 触控点击、全屏播放器手势（单击显隐、双击暂停、左右滑动快进快退、上下滑动调节音量、长按 2.0× 倍速） |
| **iPhone（iOS）** | 竖屏优先底部毛玻璃导航栏（`NavigationBar`）+ 2~3 列紧凑海报墙 + 上下流式详情页 | 单手大拇指底部导航、播放器全套触控手势与横竖屏切换 |
| **Android（手机 / 平板 / 电视）** | 由根目录原生工程构建三合一自适应 APK（支持 `.jar` `csp_` 插件与 Media3 软解扩展） | 自动识别电视遥控器、平板分栏与手机竖屏触控手势 |

## 与安卓端互通备份

在「设置 → 备份与恢复」中导出的 JSON 备份文件（`schema: marco-movie-backup`）可直接在 Windows、Mac、iPhone、iPad 与 Android 电视/手机端之间互相导入，自动同步：
- 配置地址与自定义 JSON 配置内容
- 收藏夹列表（最多 120 条）
- 观看历史与剧集播放进度（最多 60 条）
- 播放倍速、片头片尾跳过、剧集正倒序等偏好设置

## 云端自动打包（无需本机安装 Xcode 或 Visual Studio）

仓库已配置 GitHub Actions 多平台自动构建流水线（[`.github/workflows/build-multiplatform.yml`](../.github/workflows/build-multiplatform.yml)）。推送代码或在 GitHub Actions 页面点击 **Run workflow** 即可自动生成：
- `MarcoMovie-Android-universal.apk`（安卓手机 + 平板 + 电视三合一安装包）
- `MarcoMovie-Windows-x64.zip`（Windows 64 位桌面免安装版）
- `MarcoMovie-macOS-universal.zip`（Mac 苹果电脑应用包 `.app`）
- `MarcoMovie-iOS-unsigned.ipa`（iPhone / iPad 安装包，可通过爱思助手、AltStore、SideStore 或 Sideloadly 签名安装）

## 本地开发与构建命令

如果本机已安装 Flutter SDK（3.22+）：

```bash
cd flutter
python -c "import shutil; shutil.copytree('src', 'lib', dirs_exist_ok=True)"
flutter create --platforms=windows,macos,ios --project-name=marco_movie .
python -c "import shutil; shutil.copytree('src', 'lib', dirs_exist_ok=True)"
flutter pub get

# 运行单元测试
flutter test

# Windows 桌面端编译
flutter build windows --release

# macOS 桌面端编译（需在 Mac 上执行）
flutter build macos --release

# iOS / iPadOS 编译（需在 Mac 上执行）
flutter build ios --release --no-codesign
```
