# 倒车影像 iOS — 配置文件

## 文件说明

- `bisai_iosApp.swift` — App 入口，无登录，直接进入主界面
- `HomeView.swift` — 主界面（TabView：距离 + 画面）
- `CameraStreamView.swift` — WKWebView 内嵌网页
- `BafaClient.swift` — 巴法云 TCP 客户端（NWConnection）
- `Info.plist` — App 配置（HTTP 访问权限已开启）

## 当前配置

- **默认网址**: `http://10.230.31.69`
- **巴法云 UID**: `e7a1c889becf42b7b25439a0e4618c6a`
- **最低 iOS**: 16.0

## 编译说明

通过 GitHub Actions 自动编译为 IPA：

1. 将项目上传到 GitHub 仓库
2. `.github/workflows/build.yml` 会自动触发编译
3. 编译完成后在 Actions → Artifacts 下载 IPA
4. 通过 AltStore / SideStore 安装到 iPhone
