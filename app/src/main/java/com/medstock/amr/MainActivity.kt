package com.medstock.amr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Burgundy = Color(0xFF741F36)
private val Orange = Color(0xFFF0A044)
private val SoftBackground = Color(0xFFFFF9F7)

private data class Product(
    val row: Int,
    val label: String,
    val code: String,
    val strength: String,
    val form: String
)

private data class PackageCounts(val community: String = "", val hospital: String = "")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(116, 31, 54)
        window.navigationBarColor = android.graphics.Color.rgb(116, 31, 54)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Burgundy,
                    secondary = Orange,
                    background = SoftBackground,
                    surface = Color.White,
                    onPrimary = Color.White
                )
            ) {
                AmrScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp).background(Color.White)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmrScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("amr", 0) }
    val products = remember {
        val json = context.assets.open("products.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Product(
                row = item.getInt("row"),
                label = item.getString("label").trim(),
                code = item.optString("code").trim(),
                strength = item.optString("strength").trim(),
                form = item.optString("form").trim()
            )
        }.distinctBy { it.row }
    }

    val now = remember { Date() }
    val nowYear = SimpleDateFormat("yyyy", Locale.US).format(now)
    val nowMonth = SimpleDateFormat("MM", Locale.US).format(now)
    val years = remember { (2000..((nowYear.toIntOrNull() ?: 2026) + 5)).map(Int::toString) }
    val months = remember {
        listOf(
            "01 - كانون الثاني", "02 - شباط", "03 - آذار", "04 - نيسان",
            "05 - أيار", "06 - حزيران", "07 - تموز", "08 - آب",
            "09 - أيلول", "10 - تشرين الأول", "11 - تشرين الثاني", "12 - كانون الأول"
        )
    }

    var year by remember { mutableStateOf(prefs.getString("year", nowYear) ?: nowYear) }
    var month by remember { mutableStateOf(prefs.getString("month", nowMonth) ?: nowMonth) }
    var hospitalPopulation by remember { mutableStateOf(prefs.getString("hospital", "") ?: "") }
    var communityPopulation by remember { mutableStateOf(prefs.getString("community", "") ?: "") }
    var query by remember { mutableStateOf("") }
    var selectorExpanded by remember { mutableStateOf(false) }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    }
    var counts by remember {
        mutableStateOf(run {
            val saved = prefs.getString("package_counts", null)
            val restored = mutableMapOf<Int, PackageCounts>()
            if (!saved.isNullOrBlank()) {
                runCatching {
                    val obj = JSONObject(saved)
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val item = obj.optJSONObject(key) ?: continue
                        key.toIntOrNull()?.let { row ->
                            restored[row] = PackageCounts(
                                community = item.optString("community"),
                                hospital = item.optString("hospital")
                            )
                        }
                    }
                }
            }
            restored.toMap()
        })
    }
    var showHelp by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("تُحفظ البيانات محلياً على الجهاز.") }
    LaunchedEffect(year, month, hospitalPopulation, communityPopulation) {
        prefs.edit()
            .putString("year", year)
            .putString("month", month)
            .putString("hospital", hospitalPopulation)
            .putString("community", communityPopulation)
            .apply()
    }
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    val filtered = remember(products, query, favorites) {
        products.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.code.contains(query, ignoreCase = true) ||
                it.strength.contains(query, ignoreCase = true) ||
                it.form.contains(query, ignoreCase = true)
        }.sortedWith(
            compareByDescending<Product> { favorites.contains(it.row.toString()) }
                .thenBy { it.label }
        )
    }

    fun persistCounts(updated: Map<Int, PackageCounts>) {
        counts = updated
        val json = JSONObject()
        updated.forEach { (row, value) ->
            json.put(row.toString(), JSONObject().put("community", value.community).put("hospital", value.hospital))
        }
        prefs.edit().putString("package_counts", json.toString()).apply()
    }

    fun persistGeneralSettings() {
        prefs.edit()
            .putString("year", year)
            .putString("month", month)
            .putString("hospital", hospitalPopulation)
            .putString("community", communityPopulation)
            .putStringSet("favorites", favorites)
            .apply()
    }

    fun exportAndShare() {
        try {
            val selectedYear = year.toIntOrNull() ?: error("اختر السنة.")
            val selectedMonth = month.take(2).toIntOrNull() ?: error("اختر الشهر.")
            require(selectedYear in 2000..2100) { "السنة غير صحيحة." }
            require(selectedMonth in 1..12) { "الشهر غير صحيح." }

            fun parseNonNegativeWholeNumber(value: String, label: String): Double {
                val trimmed = value.trim()
                require(trimmed.isNotEmpty()) { "أدخل $label." }
                val parsed = trimmed.toLongOrNull()
                    ?: error("$label يجب أن يكون عدداً صحيحاً من دون كسور.")
                require(parsed >= 0) { "$label لا يمكن أن يكون سالباً." }
                return parsed.toDouble()
            }

            val communityPop = parseNonNegativeWholeNumber(communityPopulation, "عدد سكان المجتمع")
            val hospitalPop = parseNonNegativeWholeNumber(hospitalPopulation, "عدد سكان المستشفى")
            val parsedCounts = products.associate { product ->
                val entry = counts[product.row] ?: PackageCounts()
                val community = if (entry.community.isBlank()) 0.0
                    else parseNonNegativeWholeNumber(entry.community, "كمية المجتمع: ${product.label}")
                val hospital = if (entry.hospital.isBlank()) 0.0
                    else parseNonNegativeWholeNumber(entry.hospital, "كمية المستشفى: ${product.label}")
                product.row to (community to hospital)
            }

            val reportsDir = File(context.filesDir, "reports")
            check(reportsDir.exists() || reportsDir.mkdirs()) { "تعذر إنشاء مجلد التقارير." }
            val output = File(
                reportsDir,
                "al_habbubi_hospital_${selectedYear}_${selectedMonth.toString().padStart(2, '0')}.xlsx"
            )

            context.assets.open("amr_template.xlsx").use { input ->
                XSSFWorkbook(input).use { workbook ->
                    val populationSheet = workbook.getSheet("Population Data")
                        ?: error("ورقة Population Data غير موجودة في القالب.")
                    val populationRow = populationSheet.getRow(1) ?: populationSheet.createRow(1)
                    populationRow.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(selectedYear.toDouble())
                    populationRow.getCell(4, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(communityPop + hospitalPop)
                    populationRow.getCell(5, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(communityPop)
                    populationRow.getCell(6, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(hospitalPop)

                    val productSheet = workbook.getSheet("Product Data")
                        ?: error("ورقة Product Data غير موجودة في القالب.")
                    val headerRow = productSheet.getRow(0) ?: error("صف السنوات غير موجود في Product Data.")
                    listOf(32, 33, 34).forEach { column ->
                        headerRow.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                            .setCellValue(selectedYear.toDouble())
                    }

                    products.forEach { product ->
                        val excelRowIndex = product.row - 1
                        require(excelRowIndex >= 2) { "رقم صف غير صالح للصنف: ${product.label}" }
                        val row = productSheet.getRow(excelRowIndex)
                            ?: error("صف الصنف غير موجود في Excel: ${product.label}")
                        val values = parsedCounts[product.row] ?: (0.0 to 0.0)
                        row.getCell(32, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                            .setCellValue(values.first + values.second)
                        row.getCell(33, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                            .setCellValue(values.first)
                        row.getCell(34, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                            .setCellValue(values.second)
                    }

                    FileOutputStream(output).use { stream -> workbook.write(stream) }
                }
            }

            persistGeneralSettings()
            status = "تم إنشاء التقرير وحفظه: ${output.name}"
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.provider", output
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            shareLauncher.launch(Intent.createChooser(intent, "إرسال تقرير AMR"))
        } catch (exception: Exception) {
            status = "تعذر إنشاء التقرير: ${exception.message ?: "تحقق من البيانات"}"
        }
    }

    Scaffold(
        containerColor = SoftBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AMR", fontWeight = FontWeight.Bold)
                        Text("مقاومة مضادات المايكروبات", fontSize = 12.sp)
                    }
                },
                actions = {
                    TextButton(onClick = { showHelp = true }) {
                        Text("التعليمات", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Burgundy,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField("السنة", year, years, Modifier.weight(1f)) { year = it }
                DropdownField(
                    "الشهر",
                    months.firstOrNull { it.startsWith(month.padStart(2, '0')) } ?: months[(nowMonth.toIntOrNull() ?: 1) - 1],
                    months,
                    Modifier.weight(1.35f)
                ) { month = it.take(2) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hospitalPopulation,
                    onValueChange = { hospitalPopulation = it },
                    label = { Text("سكان المستشفى", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = communityPopulation,
                    onValueChange = { communityPopulation = it },
                    label = { Text("سكان المجتمع", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it; selectorExpanded = true },
                label = { Text("ابحث عن المضاد الحيوي") },
                placeholder = { Text("الاسم أو ATC أو الجرعة") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { selectorExpanded = !selectorExpanded },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("▼ اختيار المضاد الحيوي من القائمة", maxLines = 1)
                }
                DropdownMenu(
                    expanded = selectorExpanded,
                    onDismissRequest = { selectorExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = 360.dp).background(Color.White)
                ) {
                    filtered.take(150).forEach { product ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(product.label, fontWeight = FontWeight.Medium)
                                    Text(
                                        listOf(product.strength, product.form, product.code)
                                            .filter(String::isNotBlank).joinToString(" • "),
                                        fontSize = 11.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            },
                            onClick = { query = product.label; selectorExpanded = false }
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("المضادات", fontWeight = FontWeight.Bold, color = Burgundy)
                Spacer(Modifier.weight(1f))
                Text("${filtered.size} صنف", fontSize = 12.sp)
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(filtered, key = { it.row }) { product ->
                    val entry = counts[product.row] ?: PackageCounts()
                    val communityValue = entry.community.toLongOrNull() ?: 0L
                    val hospitalValue = entry.hospital.toLongOrNull() ?: 0L
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (product.strength.isNotBlank()) Badge(product.strength, Burgundy)
                                    if (product.form.isNotBlank()) Badge(product.form, Orange, true)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(product.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    if (product.code.isNotBlank()) {
                                        Text("ATC: ${product.code}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        favorites = if (favorites.contains(product.row.toString())) {
                                            favorites - product.row.toString()
                                        } else {
                                            favorites + product.row.toString()
                                        }
                                        prefs.edit().putStringSet("favorites", favorites).apply()
                                    },
                                    contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    Text(
                                        if (favorites.contains(product.row.toString())) "★" else "☆",
                                        color = Orange,
                                        fontSize = 21.sp
                                    )
                                }
                            }

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = entry.community,
                                    onValueChange = { value ->
                                        if (value.isEmpty() || value.all(Char::isDigit)) {
                                            persistCounts(counts.toMutableMap().apply {
                                                put(product.row, entry.copy(community = value))
                                            })
                                        }
                                    },
                                    label = { Text("كمية المجتمع", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                OutlinedTextField(
                                    value = entry.hospital,
                                    onValueChange = { value ->
                                        if (value.isEmpty() || value.all(Char::isDigit)) {
                                            persistCounts(counts.toMutableMap().apply {
                                                put(product.row, entry.copy(hospital = value))
                                            })
                                        }
                                    },
                                    label = { Text("كمية المستشفى", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("المجموع", fontSize = 10.sp, color = Color.Gray)
                                    Text(
                                        (communityValue + hospitalValue).toString(),
                                        color = Burgundy,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(status, fontSize = 11.sp, color = Color.DarkGray, maxLines = 2)
            Button(
                onClick = { exportAndShare() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Burgundy)
            ) {
                Text("حفظ وإرسال ملف Excel", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("عن التطبيق والتعليمات") },
            text = {
                Text(
                    "تطبيق محلي لإعداد تقرير وحدة السيطرة على مقاومة مضادات المايكروبات. اختر السنة والشهر وأدخل أعداد السكان. لكل صنف أدخل كمية المجتمع وكمية المستشفى بصورة منفصلة؛ يحسب التطبيق المجموع ويضع القيم في أعمدتها الصحيحة في Excel. يمكنك البحث وإضافة الأصناف للمفضلة. العنابي للجرعة/التركيز والبرتقالي للشكل الدوائي. زر حفظ وإرسال ينشئ نسخة من القالب ويحفظها داخل التطبيق ثم يفتح قائمة المشاركة.\n\nتم التصميم بواسطة الصيدلاني محمد رحيم."
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("إغلاق") }
            }
        )
    }
}

@Composable
private fun Badge(text: String, color: Color, dark: Boolean = false) {
    Box(
        Modifier.background(color, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 10.sp,
            maxLines = 2,
            color = if (dark) Color(0xFF3D2512) else Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}
