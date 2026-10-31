package com.shrivatsav.cardamom

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class CardWidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repo = CardRepository(this)
        val cards = repo.cards.value

        enableEdgeToEdge()

        setContent {
            CardamomTheme {
                Surface(Modifier.fillMaxSize(), color = C.Bg, contentColor = C.Cream) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Select Widget Card", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.Cream)
                                Spacer(Modifier.height(4.dp))
                                Text("Choose which card displays on your home screen", fontSize = 13.sp, color = C.Muted)
                            }
                            CircleIcon(Icons.Default.Close, "Cancel") { finish() }
                        }

                        if (cards.isEmpty()) {
                            Column(
                                Modifier.weight(1f).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("No cards in wallet yet", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                Text("Open Cardamom to add your first card", color = C.Muted, fontSize = 14.sp)
                            }
                        } else {
                            var selectedId by remember { mutableStateOf(cards.first().id) }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(cards, key = { it.id }) { c ->
                                    val isSelected = c.id == selectedId
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(28.dp))
                                            .then(
                                                if (isSelected) Modifier.border(3.dp, C.Yellow, RoundedCornerShape(28.dp))
                                                else Modifier
                                            )
                                            .clickable { selectedId = c.id }
                                    ) {
                                        CardTile(c, expanded = isSelected, art = true)
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(
                                text = "Place on home screen",
                                onClick = {
                                    CardWidgetProvider.saveCardId(this@CardWidgetConfigureActivity, appWidgetId, selectedId)
                                    val manager = AppWidgetManager.getInstance(this@CardWidgetConfigureActivity)
                                    CardWidgetProvider.updateAppWidget(this@CardWidgetConfigureActivity, manager, appWidgetId)

                                    val resultValue = Intent().apply {
                                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                    }
                                    setResult(Activity.RESULT_OK, resultValue)
                                    finish()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
