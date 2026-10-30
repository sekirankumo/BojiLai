# 薄肌来（BojiLai）

帮你练出薄肌：记录饮食、训练和身体数据，内置可以操控全部功能的 AI 助手。

**介绍页：<https://sekirankumo.github.io/BojiLai/>**

## 功能

- **AI 助手**：底部「问 AI」。用一句话记录饮食、体重，打卡训练，换动作，调整计划，查询数据；修改前会确认，所有修改都能撤销。可以发图片（饭菜、体重秤截图等），回答里的表格、列表会排好版。
- **饮食**：拍照 / 相册 / 语音 / 文字描述，AI 识别食物并估算热量和三大营养素，逐条确认修改后保存；常吃的食物可以收藏一键记录，在设置里修改或删除。
- **训练**：AI 根据目标、频率、器材生成计划，日历展示，精确到组数和次数；点一组打卡，长按修改实际次数和重量，也可以语音打卡。
- **动作库**：159 个常用动作，带示意图和中文要领（数据来自 free-exercise-db，公共领域）。
- **漏打卡自动调整**：每天检查，漏掉的训练由 AI 重新安排进后续计划并直接生效，在「调整记录」或对话里可以撤销。
- **身体数据**：体重、体脂记录和趋势图（7 天移动平均），可从系统的健康数据同步：安卓是 Health Connect（健康连接），iPhone 是「健康」App，小米运动健康、三星健康、体脂秤 App 等写进去的数据都能读取；在设置里开启，不会在首次打开时要权限。
- **今日**：摄入、训练热力图、身体状态一屏看完。
- **备份**：每天自动备份（安卓在「下载/薄肌来」，iPhone 在「文件」App 的「我的 iPhone › 薄肌来」），也可以手动导出 / 恢复。

## 技术

- Kotlin Multiplatform + Compose Multiplatform：安卓和 iOS 共用一套界面和逻辑（`app/src/commonMain`），系统相关的部分分别在 `androidMain`、`iosMain` 和 `iosApp/`（Swift）
- 数据全部存在手机本地（Room）
- 语音输入：安卓用 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 在手机上离线识别（Zipformer 中文模型，第一次使用时下载约 26MB）；iPhone 用系统自带的语音识别。都不用 API Key
- AI 服务自己填 API Key，支持任何 OpenAI 兼容接口（智谱、硅基流动、通义、DeepSeek、Gemini、OpenRouter、Groq 等）
- 推送到 `main` 后由 GitHub Actions 自动编译签名并发布 APK，接着在 macOS 上编出 IPA 附到同一个 Release；`dev` 等其他分支只做编译检查（iOS 还会在模拟器上截图）

## 安装

**安卓**：到 [Releases](../../releases/latest) 下载最新的 `BojiLai-x.y.z.apk` 安装。之后的版本在 App 里提示更新即可。

**iPhone**：到 [Releases](../../releases/latest) 下载 `BojiLai-x.y.z.ipa`（没有签名），用电脑上的 [Sideloadly](https://sideloadly.io/)（Windows / Mac）登录自己的 Apple ID 安装到 iPhone：

1. iPhone 用数据线连电脑，打开 Sideloadly，把 IPA 拖进去，填 Apple ID，点 Start。
2. 第一次装好后，在 iPhone 的 设置 → 通用 → VPN 与设备管理 里信任这个开发者；iOS 16 起还要在 设置 → 隐私与安全性 里打开「开发者模式」。
3. 免费 Apple ID 签的 App 7 天后会打不开，用 Sideloadly 重新装一次就行（数据不会丢）；付费开发者账号签的一年有效。

有新版本时 App 里会提示，点「去下载」下载新的 IPA，用同样的方法覆盖安装。

## 本地开发

先运行 `tools/fetch_sherpa.sh` 下载语音识别库（约 37MB，不入库），再用 Android Studio 打开本目录。如需与正式版互相覆盖安装，在项目根目录放 `release.jks` 和 `keystore.properties`（不入库）。

iOS 需要 macOS 和 Xcode：`brew install xcodegen`，在 `iosApp/` 里运行 `xcodegen generate`，再用 Xcode 打开 `iosApp/BojiLai.xcodeproj`（编译时会自动调用 Gradle 编 Kotlin 部分）。

动作库资源（`app/src/commonMain/composeResources/files`）由 `tools/build_exercises.py` 生成：中文名称和要领在 `tools/exercises_zh.txt`，修改后运行

```
python tools/build_exercises.py <free-exercise-db 目录> app/src/commonMain/composeResources/files
```

## 数据库迁移

改表结构时把 `AppDatabase` 的 version + 1，并在 `MIGRATION_SQL` 里写迁移 SQL（安卓和 iOS 共用）。CI 的编译检查会上传 `room-schemas`，可以用来核对建表语句。

