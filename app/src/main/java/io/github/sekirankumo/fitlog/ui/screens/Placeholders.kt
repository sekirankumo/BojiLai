package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
private fun ComingSoon(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun TodayScreen() = ComingSoon("今日", "今日摄入、训练和体重概览将在 M7 完成")

@Composable
fun DietScreen() = ComingSoon("饮食记录", "拍照 / 语音 / 文字识别将在 M2 完成")

@Composable
fun TrainingScreen() = ComingSoon("训练计划", "日历、打卡和 AI 生成计划将在 M4 完成")

@Composable
fun BodyScreen() = ComingSoon("身体数据", "体重体脂记录和趋势图将在 M1 完成")

@Composable
fun AssistantScreen() = ComingSoon("AI 助手", "可以操控全部功能的 AI 助手将从 M1 开始逐步接入")
