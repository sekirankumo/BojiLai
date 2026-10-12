# FitLog

个人用安卓 App：记录饮食、训练和身体数据，内置可以操控全部功能的 AI 助手。

- Kotlin + Jetpack Compose，数据全部存在手机本地（Room）
- AI 服务自己填 API Key，支持 OpenAI 兼容接口
- 推送到 `main` 后由 GitHub Actions 自动编译签名并发布，App 内一键更新

## 安装

到 [Releases](../../releases/latest) 下载最新的 `FitLog-x.y.z.apk` 安装。之后的版本在 App 里提示更新即可。

## 本地开发

用 Android Studio 打开本目录即可。如需与正式版互相覆盖安装，在项目根目录放 `release.jks` 和 `keystore.properties`（不入库）。

## 开发阶段

| 阶段 | 内容 |
|---|---|
| M0 | 骨架、导航、数据库、自动发布和应用内更新 |
| M1 | AI 服务配置、助手框架、身体数据 |
| M2 | 饮食识别（拍照 / 语音 / 文字） |
| M3 | 动作库（中文说明 + 图片） |
| M4 | 训练计划、日历、打卡（含语音打卡） |
| M5 | 漏打卡自动重排、版本历史与撤销 |
| M6 | Health Connect（小米运动健康）同步 |
| M7 | 今日首页、备份、提醒、打磨 |
