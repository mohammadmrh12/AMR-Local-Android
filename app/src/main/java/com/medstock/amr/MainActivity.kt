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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
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
            label = { Text(label, fontSize = 12.sp, maxLines = 1) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            singleLine = true
        )
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
        listOf("01 - January", "02 - February", "03 - March", "04 - April", "05 - May", "06 - June", "07 - July", "08 - August", "09 - September", "10 - October", "11 - November", "12 - December")
    }

    var year by remember { mutableStateOf(prefs.getString("year", nowYear) ?: nowYear) }
    var month by remember { mutableStateOf(prefs.getString("month", nowMonth) ?: nowMonth) }
    var hospitalPopulation by remember { mutableStateOf(prefs.getString("hospital", "") ?: "") }
    var communityPopulation by remember { mutableStateOf(prefs.getString("community", "") ?: "") }
    var query by remember { mutableStateOf("") }
    var hospitalName by remember { mutableStateOf((prefs.getString("hospital_name", "al habbubi hospital") ?: "al habbubi hospital").let { if (it.contains("مستشفى") || it.contains("الحبوبي")) "al habbubi hospital" else it }) }
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
    var status by remember { mutableStateOf("Data is saved locally on this device.") }
    LaunchedEffect(year, month, hospitalPopulation, communityPopulation, hospitalName) {
        prefs.edit()
            .putString("year", year)
            .putString("month", month)
            .putString("hospital", hospitalPopulation)
            .putString("community", communityPopulation)
            .putString("hospital_name", hospitalName)
            .apply()
    }
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    val filtered = remember(products, favorites) {
        products.sortedWith(
            compareByDescending<Product> { favorites.contains(it.row.toString()) }
                .thenBy { it.row }
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
            .putString("hospital_name", hospitalName)
            .putStringSet("favorites", favorites)
            .apply()
    }

    fun exportAndShare() {
        try {
            val selectedYear = year.toIntOrNull() ?: error("Select a year.")
            val selectedMonth = month.take(2).toIntOrNull() ?: error("Select a month.")
            require(selectedYear in 2000..2100) { "Invalid year." }
            require(selectedMonth in 1..12) { "Invalid month." }

            fun parseNonNegativeWholeNumber(value: String, label: String): Double {
                val trimmed = value.trim()
                require(trimmed.isNotEmpty()) { "Enter $label." }
                val parsed = trimmed.toLongOrNull()
                    ?: error("$label must be a whole number.")
                require(parsed >= 0) { "$label cannot be negative." }
                return parsed.toDouble()
            }

            val communityPop = if (communityPopulation.isBlank()) null else parseNonNegativeWholeNumber(communityPopulation, "community population")
            val hospitalPop = if (hospitalPopulation.isBlank()) null else parseNonNegativeWholeNumber(hospitalPopulation, "hospital population")
            val parsedCounts = products.associate { product ->
                val entry = counts[product.row] ?: PackageCounts()
                val community = if (entry.community.isBlank()) null
                    else parseNonNegativeWholeNumber(entry.community, "community quantity: ${product.label}")
                val hospital = if (entry.hospital.isBlank()) null
                    else parseNonNegativeWholeNumber(entry.hospital, "hospital quantity: ${product.label}")
                product.row to (community to hospital)
            }

            val reportsDir = File(context.filesDir, "reports")
            check(reportsDir.exists() || reportsDir.mkdirs()) { "Could not create reports folder." }
            val output = File(
                reportsDir,
                "${hospitalName.trim().ifBlank { "al habbubi hospital" }.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')}_${selectedYear}_${selectedMonth.toString().padStart(2, '0')}.xlsx"
            )

            context.assets.open("amr_template.xlsx").use { input ->
                XSSFWorkbook(input).use { workbook ->
                    val populationSheet = workbook.getSheet("Population Data")
                        ?: error("Population Data sheet is missing from the template.")
                    val populationRow = populationSheet.getRow(1) ?: populationSheet.createRow(1)
                    populationRow.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(selectedYear.toDouble())
                    if (communityPop == null && hospitalPop == null) populationRow.getCell(4)?.setBlank() else populationRow.getCell(4, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue((communityPop ?: 0.0) + (hospitalPop ?: 0.0))
                    if (communityPop == null) populationRow.getCell(5)?.setBlank() else populationRow.getCell(5, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(communityPop)
                    if (hospitalPop == null) populationRow.getCell(6)?.setBlank() else populationRow.getCell(6, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(hospitalPop)

                    val productSheet = workbook.getSheet("Product Data")
                        ?: error("Product Data sheet is missing from the template.")
                    val headerRow = productSheet.getRow(0) ?: error("Year header is missing from Product Data.")
                    listOf(32, 33, 34).forEach { column ->
                        headerRow.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                            .setCellValue(selectedYear.toDouble())
                    }
                    workbook.setForceFormulaRecalculation(true)

                    products.forEach { product ->
                        val excelRowIndex = product.row - 1
                        require(excelRowIndex >= 2) { "رقم صف غير صالح للصنف: ${product.label}" }
                        val row = productSheet.getRow(excelRowIndex)
                            ?: error("Product row is missing in Excel: ${product.label}")
                        val values = parsedCounts[product.row] ?: (null to null)
                        if (values.first == null && values.second == null) {
                            row.getCell(32)?.setBlank()
                            row.getCell(33)?.setBlank()
                            row.getCell(34)?.setBlank()
                        } else {
                            val community = values.first ?: 0.0
                            val hospital = values.second ?: 0.0
                            row.getCell(32, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(community + hospital)
                            if (values.first == null) row.getCell(33)?.setBlank()
                            else row.getCell(33, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(community)
                            if (values.second == null) row.getCell(34)?.setBlank()
                            else row.getCell(34, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(hospital)
                        }
                    }

                    FileOutputStream(output).use { stream -> workbook.write(stream) }
                }
            }

            persistGeneralSettings()
            status = "Report created: ${output.name}"
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.provider", output
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            shareLauncher.launch(Intent.createChooser(intent, "Share AMR report"))
        } catch (exception: Exception) {
            status = "Could not create report: ${exception.message ?: "Check the entered data"}"
        }
    }

    Scaffold(
        containerColor = SoftBackground,
        floatingActionButtonPosition = FabPosition.Start,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { exportAndShare() },
                icon = { Icon(Icons.Default.Share, contentDescription = "Save and share Excel report") },
                text = { Text("Save & Share") },
                shape = RoundedCornerShape(50),
                containerColor = Burgundy,
                contentColor = Color.White
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AMR", fontWeight = FontWeight.Bold)
                        Text("Antimicrobial Resistance", fontSize = 12.sp)
                    }
                },
                actions = {
                    TextButton(onClick = { showHelp = true }) {
                        Text("Help", color = Color.White)
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
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField("Year", year, years, Modifier.weight(1f)) { year = it }
                DropdownField(
                    "Month",
                    months.firstOrNull { it.startsWith(month.padStart(2, '0')) } ?: months[(nowMonth.toIntOrNull() ?: 1) - 1],
                    months,
                    Modifier.weight(1.35f)
                ) { month = it.take(2) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hospitalPopulation,
                    onValueChange = { hospitalPopulation = it },
                    label = { Text("Hospital population", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f).height(68.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = communityPopulation,
                    onValueChange = { communityPopulation = it },
                    label = { Text("Community population", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f).height(68.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            OutlinedTextField(
                value = hospitalName,
                onValueChange = { hospitalName = it },
                label = { Text("Hospital name in report") },
                modifier = Modifier.fillMaxWidth().height(68.dp),
                singleLine = true
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Antibiotics", fontWeight = FontWeight.Bold, color = Burgundy)
                Spacer(Modifier.weight(1f))
                Text("${filtered.size} items", fontSize = 12.sp)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                filtered.forEach { product ->
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
                                    if (product.strength.isNotBlank()) Badge(product.strength, Color(0xFFE8EDF0), true)
                                    if (product.form.isNotBlank()) Badge(product.form, Color(0xFFE8EDF0), true)
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
                                    label = { Text("Community qty", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f).height(68.dp),
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
                                    label = { Text("Hospital qty", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f).height(68.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Total", fontSize = 10.sp, color = Color.Gray)
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
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("About & Instructions") },
            text = {
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "AMR report preparation app. Select the reporting year and month, enter population figures, and enter antibiotic quantities when available. Quantity fields left empty remain blank in the Excel report. All antibiotics are shown, with starred favorites at the top. The hospital name can be edited on the main screen.\n\nExcel worksheet guide:\n• Macro: General report definitions and indicators.\n• Availability Data: Antibiotic availability information.\n• Population Data: Reporting year and total, community, and hospital population figures.\n• Product Data: Product list, codes, and yearly quantity columns.\n• ATC: Anatomical Therapeutic Chemical classification.\n• DDD: Defined Daily Dose, a standard measurement for drug-use statistics, not necessarily a patient dose.\n• DDD Combination: DDD calculations for combination products.\n• Conversion: Conversion factors used in report calculations.\n\nEnter non-negative whole numbers. Use Save & Share to generate the Excel report and open the sharing menu.\n\nDesigned by Pharmacist Mohammad Raheem."
                )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Close") }
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
