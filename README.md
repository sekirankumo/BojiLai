# 薄肌来（BojiLai）

帮你练出薄肌：记录饮食、训练和身体数据，内置可以操控全部功能的 AI 助手。

**介绍页：<https://sekirankumo.github.io/BojiLai/>**

## 功能

- **AI 助手**：底部「问 AI」。用一句话记录饮食、体重，打卡训练，换动作，调整计划，查询数据；修改前会确认，所有修改都能撤销。可以发图片（饭菜、体重秤截图等），回答里的表格、列表会排好版。
- **饮食**：拍照 / 相册 / 语音 / 文字描述，AI 识别食物并估算热量和三大营养素，逐条确认修改后保存；常吃的食物可以收藏一键记录。
- **训练**：AI 根据目标、频率、器材生成计划，日历展示，精确到组数和次数；点一组打卡，长按修改实际次数和重量，也可以语音打卡。
- **动作库**：159 个常用动作，带示意图和中文要领（数据来自 free-exercise-db，公共领域）。
- **漏打卡自动调整**：每天检查，漏掉的训练由 AI 重新安排进后续计划并直接生效，在「调整记录」或对话里可以撤销。
- **身体数据**：体重、体脂记录和趋势图（7 天移动平均），可从 Health Connect（小米运动健康）同步。
- **今日**：摄入、训练、体重、步数一屏看完。
- **备份**：每天自动备份到「下载/薄肌来」，也可以手动导出 / 恢复。

## 技术

- Kotlin + Jetpack Compose，数据全部存在手机本地（Room）
- 语音输入用 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 在手机上离线识别（Zipformer 中文模型，第一次使用时下载约 26MB），不用 API Key
- AI 服务自己填 API Key，支持任何 OpenAI 兼容接口（智谱、硅基流动、通义、DeepSeek、Gemini、OpenRouter、Groq 等）
- 推送到 `main` 后由 GitHub Actions 自动编译签名并发布，App 内一键更新；`dev` 等其他分支只做编译检查

## 安装

到 [Releases](../../releases/latest) 下载最新的 `BojiLai-x.y.z.apk` 安装。之后的版本在 App 里提示更新即可。

## 本地开发

先运行 `tools/fetch_sherpa.sh` 下载语音识别库（约 37MB，不入库），再用 Android Studio 打开本目录。如需与正式版互相覆盖安装，在项目根目录放 `release.jks` 和 `keystore.properties`（不入库）。

动作库资源由 `tools/build_exercises.py` 生成：中文名称和要领在 `tools/exercises_zh.txt`，修改后运行

```
python tools/build_exercises.py <free-exercise-db 目录> app/src/main/assets
```

## 数据库迁移

改表结构时把 `AppDatabase` 的 version + 1，并在 `MIGRATIONS` 里写迁移 SQL。CI 的编译检查会上传 `room-schemas`，可以用来核对建表语句。

