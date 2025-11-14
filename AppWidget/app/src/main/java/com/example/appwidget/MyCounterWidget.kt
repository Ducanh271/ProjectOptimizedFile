package com.example.appwidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.unit.dp
import androidx.glance.Button  // Thêm import này
import androidx.glance.LocalContext
import androidx.glance.LocalGlanceId
import kotlinx.coroutines.runBlocking

class MyCounterWidget : GlanceAppWidget() {

    companion object {
        val COUNT_KEY = PreferencesGlanceStateDefinition.stringPreferencesKey("count_key")  // Sửa lại key
    }

    override val stateDefinition: GlanceStateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                Content()  // Gọi hàm @Composable
            }
        }
    }

    @Composable
    private fun Content() {
        val context = LocalContext.current
        val glanceId = LocalGlanceId.current
        val prefs = currentState(PreferencesGlanceStateDefinition)  // Lấy trạng thái
        val count = prefs[COUNT_KEY]?.toIntOrNull() ?: 0  // Sử dụng toIntOrNull thay vì toInt

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(0xFFE0E0E0))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Số: $count",
                style = TextStyle(fontSize = 24.dp)
            )
            Button(
                text = "Reset",
                onClick = actionStartActivity<ResetActivity>(context),  // Gọi Activity để reset
                modifier = GlanceModifier.padding(top = 16.dp)
            )
        }
    }
}

// Receiver
class MyCounterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MyCounterWidget()
}

// Activity để xử lý reset (tạo file mới: ResetActivity.kt)
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.glance.appwidget.updateAll

class ResetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ResetScreen()
        }
    }

    @Composable
    fun ResetScreen() {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Xác nhận reset?")
            Button(onClick = {
                runBlocking {
                    MyCounterWidget().updateAll(this@ResetActivity) { prefs ->
                        prefs[MyCounterWidget.COUNT_KEY] = "0"  // Reset về 0
                    }
                }
                finish()  // Đóng Activity
            }) {
                Text("Đồng ý")
            }
        }
    }
}