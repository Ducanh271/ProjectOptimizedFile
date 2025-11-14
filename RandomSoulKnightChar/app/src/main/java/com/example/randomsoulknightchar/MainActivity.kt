package com.example.randomsoulknightchar

import android.content.ContentValues.TAG
import androidx.core.content.edit
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.example.randomsoulknightchar.ui.theme.RandomSoulKnightCharTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import kotlin.random.Random

data class Skill(val name: String, val imageUrl: String)

data class Character(
    val name: String,
    val imageUrl: String,
    val skills: List<Skill> // List of skills instead of a single skill
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RandomSoulKnightCharTheme {
                RandomScreen()
            }
        }
    }
}

// Lưu dữ liệu vào SharedPreferences
private fun saveCharacters(context: Context, characters: List<Character>) {
    Log.d("SoulKnightDebug", "Lưu dữ liệu vào SharedPreferences: ${characters.size} nhân vật")
    context.getSharedPreferences("SoulKnightChars", Context.MODE_PRIVATE).edit {
        // Lưu danh sách kỹ năng dưới dạng chuỗi phân tách
        putString("characters", characters.joinToString("||") {
            val skillsString = it.skills.joinToString(";;") { skill -> "${skill.name}:${skill.imageUrl}" }
            "${it.name}::${it.imageUrl}::${skillsString}"
        })
    }
}

// Đọc dữ liệu từ SharedPreferences
private fun loadCharacters(context: Context): List<Character> {
    val sharedPreferences = context.getSharedPreferences("SoulKnightChars", Context.MODE_PRIVATE)
    val data = sharedPreferences.getString("characters", null)
    Log.d("SoulKnightDebug", "Đọc dữ liệu từ SharedPreferences: $data")
    if (data == null) {
        Log.d("SoulKnightDebug", "Không có dữ liệu trong SharedPreferences")
        return emptyList()
    }
    val characters = data.split("||").mapNotNull {
        val parts = it.split("::")
        if (parts.size == 3) {
            val name = parts[0]
            val imageUrl = parts[1]
            val skills = parts[2].split(";;").mapNotNull { skillData ->
                val skillParts = skillData.split(":")
                if (skillParts.size == 2) Skill(skillParts[0], skillParts[1]) else null
            }
            Character(name, imageUrl, skills)
        } else {
            null
        }
    }
    Log.d("SoulKnightDebug", "Danh sách nhân vật từ SharedPreferences: ${characters.size} nhân vật")
    return characters
}

// Xóa dữ liệu trong SharedPreferences để debug
private fun clearCharacters(context: Context) {
    Log.d("SoulKnightDebug", "Xóa dữ liệu trong SharedPreferences")
    context.getSharedPreferences("SoulKnightChars", Context.MODE_PRIVATE).edit {
        clear()
    }
}

// Scrape dữ liệu từ web
suspend fun scrapeSoulKnightCharacters(): List<Character> {
    return withContext(Dispatchers.IO) {
        val characters = mutableListOf<Character>()
        try {
            Log.d("SoulKnightDebug", "Bắt đầu scrape dữ liệu từ trang web...")
            val doc = Jsoup.connect("https://soul-knight.fandom.com/wiki/Characters").get()
            Log.d("SoulKnightDebug", "Tải trang web thành công")
            val tables = doc.select("table.sk-table").drop(1)
            val table = tables.firstOrNull { it.classNames().contains("sortable") }
            if (table == null) {
                Log.d("SoulKnightDebug", "Không tìm thấy bảng wikitable")
                return@withContext characters
            }
            Log.d("SoulKnightDebug", "Tìm thấy bảng wikitable")
            val rows = table.select("tbody > tr")
            for ((index, row) in rows.withIndex()) {
                val cells = row.select("td")
                val rowData = cells.joinToString(" | ") { it.text().trim() }
                Log.d(TAG, "Row $index: $rowData")
            }
            Log.d("SoulKnightDebug", "Số hàng tìm thấy: ${rows.size}")
            rows.forEachIndexed { index, row ->
                val columns = row.select("td")
                Log.d("SoulKnightDebug", "Hàng $index: Số cột = ${columns.size}")
                if (columns.size >= 3) {
                    val name = columns[0].text().trim()
                    val imageUrl = columns[1].select("img").attr("src").ifEmpty {
                        columns[0].select("img").attr("data-src")
                    }
                    val tooltipCells = row.select("td span.tooltip").dropLast(1)
                    val skillList = mutableListOf<Skill>()
                    if (tooltipCells.isEmpty()){
                        Log.d("BUG","Ko load dc skill")}
                    for ((i, td) in tooltipCells.withIndex()) {
                        val img = td.selectFirst("a > img")?.attr("data-src") ?: ""
                        val skillName = td.selectFirst("span.tooltiptext")?.text()?.trim() ?: "No name"
                        if (img.isNotEmpty()) {
                            skillList.add(Skill(skillName, img))
                        }
                        Log.d("Row $index", "Skill $i: $skillName | Image: $img")
                    }
                    Log.d("SoulKnightDebug", "Nhân vật: $name, Image URL: $imageUrl, Số kỹ năng: ${skillList.size}")
                    if (imageUrl.isNotEmpty()) {
                        characters.add(Character(name, imageUrl, skillList))
                    } else {
                        Log.d("SoulKnightDebug", "Bỏ qua nhân vật $name vì không có URL ảnh")
                    }
                } else {
                    Log.d("SoulKnightDebug", "Hàng $index không đủ 3 cột, bỏ qua")
                }
            }
        } catch (e: IOException) {
            Log.e("SoulKnightDebug", "Lỗi khi scrape: ${e.message}", e)
        }
        Log.d("SoulKnightDebug", "Tổng số nhân vật scrape được: ${characters.size}")
        characters
    }
}
@Composable
fun LoadingScreen(isLoading: Boolean) {
    if (isLoading) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(R.drawable.pet_loading) // file GIF trong drawable
                .decoderFactory(GifDecoder.Factory())
                .build(),
            contentDescription = "Loading...",
            modifier = Modifier.size(100.dp)
        )
    }
}

@Composable
fun RandomScreen() {
    var characters by remember { mutableStateOf<List<Character>>(emptyList()) }
    var randomCharacter by remember { mutableStateOf<Character?>(null) }
    var randomSkill by remember { mutableStateOf<Skill?>(null) } // Add state for random skill
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }



    val context = LocalContext.current

    LaunchedEffect(Unit) {
        Log.d("SoulKnightDebug", "Bắt đầu tải dữ liệu trong LaunchedEffect")
        clearCharacters(context)
        characters = loadCharacters(context)
        if (characters.isEmpty()) {
            Log.d("SoulKnightDebug", "Không có dữ liệu trong SharedPreferences, bắt đầu scrape...")
            try {
                val scrapedCharacters = scrapeSoulKnightCharacters()
                if (scrapedCharacters.isNotEmpty()) {
                    Log.d("SoulKnightDebug", "Scrape thành công, lưu dữ liệu...")
                    characters = scrapedCharacters
                    saveCharacters(context, characters)
                } else {
                    Log.d("SoulKnightDebug", "Scrape không tìm thấy nhân vật")
                    errorMessage = "Không tìm thấy nhân vật nào."
                }
            } catch (e: Exception) {
                Log.e("SoulKnightDebug", "Lỗi khi tải dữ liệu: ${e.message}", e)
                errorMessage = "Lỗi khi tải dữ liệu: ${e.message}"
            }
        } else {
            Log.d("SoulKnightDebug", "Dữ liệu đã được tải từ SharedPreferences")
        }
        if (characters.isNotEmpty()) {
            val selectedCharacter = characters[Random.nextInt(characters.size)]
            randomCharacter = selectedCharacter
            randomSkill = if (selectedCharacter.skills.isNotEmpty()) {
                selectedCharacter.skills[Random.nextInt(selectedCharacter.skills.size)]
            } else {
                null
            }
            Log.d("SoulKnightDebug", "Chọn nhân vật ngẫu nhiên: ${randomCharacter?.name}, Kỹ năng: ${randomSkill?.name}")
        }
        isLoading = false
        Log.d("SoulKnightDebug", "Hoàn tất tải dữ liệu, isLoading = false")
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Random Soul Knight Character",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(128.dp))

        if (isLoading) {
            LoadingScreen(true)
        } else if (errorMessage != null) {
            Text(text = errorMessage!!, fontSize = 16.sp, color = androidx.compose.ui.graphics.Color.Red)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    randomCharacter?.let { character ->
                        AsyncImage(
                            model = character.imageUrl,
                            contentDescription = character.name,
                            modifier = Modifier.size(150.dp),

                            onError = {
                                errorMessage = "Lỗi tải ảnh: ${it.result.throwable.message}"
                                Log.e("SoulKnightDebug", "Lỗi tải ảnh cho ${character.name}: ${it.result.throwable.message}")
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = character.name, fontSize = 16.sp)
                    } ?: run {
                        Text(text = "Không có nhân vật", fontSize = 16.sp)
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    randomSkill?.let { skill ->
                        AsyncImage(
                            model = skill.imageUrl,
                            contentDescription = skill.name,
                            modifier = Modifier.size(50.dp),

                            onError = {
                                errorMessage = "Lỗi tải ảnh kỹ năng: ${it.result.throwable.message}"
                                Log.e("SoulKnightDebug", "Lỗi tải ảnh kỹ năng: ${it.result.throwable.message}")
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = skill.name,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    } ?: run {
                        Text(text = "Không có kỹ năng", fontSize = 16.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(128.dp))

        Button(
            onClick = {
                if (characters.isNotEmpty()) {
                    isLoading = true
                    val selectedCharacter = characters[Random.nextInt(characters.size)]
                    randomCharacter = selectedCharacter
                    randomSkill = if (selectedCharacter.skills.isNotEmpty()) {
                        selectedCharacter.skills[Random.nextInt(selectedCharacter.skills.size)]
                    } else {
                        null
                    }
                    isLoading = false
                    Log.d("SoulKnightDebug", "Nhấn Random, chọn nhân vật mới: ${randomCharacter?.name}, Kỹ năng: ${randomSkill?.name}")
                } else {
                    Log.d("SoulKnightDebug", "Nhấn Random nhưng không có nhân vật để chọn")
                }
            },
            modifier = Modifier
                .height(60.dp)
                .fillMaxWidth(),
            enabled = !isLoading && errorMessage == null
        ) {
            Text(text = "Random", fontSize = 20.sp)
        }
    }
}