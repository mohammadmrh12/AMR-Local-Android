package com.medstock.amr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
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
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Burgundy = Color(0xFF741F36)
private val Orange = Color(0xFFF0A044)
private val SoftBackground = Color(0xFFFFF9F7)
data class Product(val row:Int,val label:String,val code:String,val strength:String,val form:String)

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState)
  window.statusBarColor=android.graphics.Color.rgb(116,31,54); window.navigationBarColor=android.graphics.Color.rgb(116,31,54)
  setContent { MaterialTheme(colorScheme=lightColorScheme(primary=Burgundy,secondary=Orange,background=SoftBackground,surface=Color.White,onPrimary=Color.White)) { AmrScreen() } }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DropdownField(label:String,value:String,options:List<String>,modifier:Modifier=Modifier,onSelected:(String)->Unit) {
 var expanded by remember { mutableStateOf(false) }
 Box(modifier) {
  OutlinedTextField(value=value,onValueChange={},readOnly=true,label={Text(label,fontSize=12.sp)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},modifier=Modifier.fillMaxWidth(),singleLine=true)
  Box(Modifier.matchParentSize().clickable { expanded=true })
  DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=360.dp).background(Color.White)) {
   options.forEach { option -> DropdownMenuItem(text={Text(option)},onClick={onSelected(option);expanded=false}) }
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AmrScreen() {
 val context=LocalContext.current
 val products= remember {
  val arr=JSONArray(context.assets.open("products.json").bufferedReader().use{it.readText()})
  (0 until arr.length()).map { i -> val o=arr.getJSONObject(i); Product(o.getInt("row"),o.getString("label"),o.optString("code"),o.optString("strength"),o.optString("form")) }
 }
 val prefs=remember { context.getSharedPreferences("amr",0) }
 val nowYear=SimpleDateFormat("yyyy",Locale.US).format(Date())
 val nowMonth=SimpleDateFormat("MM",Locale.US).format(Date())
 val years=remember { (2020..2035).map{it.toString()} }
 val months=remember { listOf("01 - كانون الثاني","02 - شباط","03 - آذار","04 - نيسان","05 - أيار","06 - حزيران","07 - تموز","08 - آب","09 - أيلول","10 - تشرين الأول","11 - تشرين الثاني","12 - كانون الأول") }
 var year by remember { mutableStateOf(prefs.getString("year",nowYear)?:nowYear) }
 var month by remember { mutableStateOf(prefs.getString("month",nowMonth)?:nowMonth) }
 var hospital by remember { mutableStateOf(prefs.getString("hospital","")?:"") }
 var community by remember { mutableStateOf(prefs.getString("community","")?:"") }
 var query by remember { mutableStateOf("") }
 var selectorExpanded by remember { mutableStateOf(false) }
 var favorites by remember { mutableStateOf(prefs.getStringSet("favorites",emptySet())?.toSet()?:emptySet()) }
 var quantities by remember { mutableStateOf(mapOf<Int,String>()) }
 var showHelp by remember { mutableStateOf(false) }
 var status by remember { mutableStateOf("تُحفظ البيانات محلياً على الجهاز.") }
 val shareLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
 val filtered=remember(products,query,favorites) { products.filter { it.label.contains(query,true)||it.code.contains(query,true)||it.strength.contains(query,true)||it.form.contains(query,true) }.sortedWith(compareByDescending<Product>{favorites.contains(it.row.toString())}.thenBy{it.label}) }

 fun exportAndShare() {
  try {
   val y=year.toIntOrNull()?:error("اختر السنة.")
   val m=month.take(2).toIntOrNull()?:error("اختر الشهر.")
   require(y in 2000..2100){"السنة غير صحيحة."}; require(m in 1..12){"الشهر غير صحيح."}
   val dir=File(context.filesDir,"reports").apply{mkdirs()}
   val out=File(dir,"al habbubi hospital_${y}_${m.toString().padStart(2,'0')}.xlsx")
   context.assets.open("amr_template.xlsx").use { input -> XSSFWorkbook(input).use { book ->
    book.getSheet("Macro")?.getRow(1)?.getCell(4)?.setCellValue(y.toDouble())
    val pop=book.getSheet("Population Data"); val prow=pop.getRow(1)?:pop.createRow(1)
    prow.getCell(1,org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(y.toDouble())
    prow.getCell(5,org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(community.toDoubleOrNull()?:0.0)
    prow.getCell(6,org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(hospital.toDoubleOrNull()?:0.0)
    val data=book.getSheet("Product Data"); val header=data.getRow(0)
    listOf(32,33,34).forEach{col->header.getCell(col,org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(y.toDouble())}
    products.forEach { p -> val row=data.getRow(p.row-1)?:data.createRow(p.row-1); val cell=row.getCell(34,org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK); val v=quantities[p.row]?.trim()?.toDoubleOrNull(); if(v!=null)cell.setCellValue(v)else cell.setBlank() }
    FileOutputStream(out).use{book.write(it)}
   } }
   prefs.edit().putString("year",year).putString("month",month.take(2)).putString("hospital",hospital).putString("community",community).putStringSet("favorites",favorites).apply()
   status="تم حفظ التقرير داخل التطبيق: ${out.name}"
   val uri=androidx.core.content.FileProvider.getUriForFile(context,context.packageName+".provider",out)
   val intent=Intent(Intent.ACTION_SEND).apply{type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
   shareLauncher.launch(Intent.createChooser(intent,"إرسال تقرير AMR"))
  } catch(e:Exception) { status="تعذر إنشاء الملف: ${e.message?:"تحقق من البيانات"}" }
 }

 Scaffold(containerColor=SoftBackground,topBar={ androidx.compose.material3.TopAppBar(title={Column{Text("AMR",fontWeight=FontWeight.Bold);Text("مقاومة مضادات المايكروبات",fontSize=12.sp)}},actions={TextButton(onClick={showHelp=true}){Text("التعليمات",color=Color.White)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Burgundy,titleContentColor=Color.White)) }) { pad ->
  Column(Modifier.padding(pad).fillMaxSize().padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
    DropdownField("السنة",year,years,Modifier.weight(1f)){year=it}
    DropdownField("الشهر",months.firstOrNull{it.startsWith(month.padStart(2,'0'))}?:months[(nowMonth.toIntOrNull()?:1)-1],months,Modifier.weight(1.35f)){month=it.take(2)}
   }
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
    OutlinedTextField(hospital,{hospital=it},label={Text("سكان المستشفى",fontSize=12.sp)},modifier=Modifier.weight(1f),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
    OutlinedTextField(community,{community=it},label={Text("سكان المجتمع",fontSize=12.sp)},modifier=Modifier.weight(1f),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
   }
   OutlinedTextField(query,{query=it;selectorExpanded=true},label={Text("ابحث عن المضاد الحيوي")},placeholder={Text("الاسم أو ATC أو الجرعة")},modifier=Modifier.fillMaxWidth(),singleLine=true)
   Box(Modifier.fillMaxWidth()) {
    OutlinedButton(onClick={selectorExpanded=!selectorExpanded},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(10.dp)){Text("▼ اختيار المضاد الحيوي من القائمة",maxLines=1)}
    DropdownMenu(expanded=selectorExpanded,onDismissRequest={selectorExpanded=false},modifier=Modifier.fillMaxWidth(.92f).heightIn(max=360.dp).background(Color.White)) {
     filtered.take(150).forEach { p -> DropdownMenuItem(text={Column{Text(p.label,fontWeight=FontWeight.Medium);Text(listOf(p.strength,p.form,p.code).filter{it.isNotBlank()}.joinToString(" • "),fontSize=11.sp,color=Color.DarkGray)}},onClick={query=p.label;selectorExpanded=false}) }
    }
   }
   Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){Text("المضادات",fontWeight=FontWeight.Bold,color=Burgundy);Spacer(Modifier.weight(1f));Text("${filtered.size} صنف",fontSize=12.sp)}
   LazyColumn(modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(bottom=4.dp)) {
    items(filtered,key={it.row}) { p ->
     Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(defaultElevation=1.dp)) {
      Row(Modifier.fillMaxWidth().padding(horizontal=9.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
       Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(4.dp)) { if(p.strength.isNotBlank())Badge(p.strength,Burgundy);if(p.form.isNotBlank())Badge(p.form,Orange,true) }
       Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(p.label,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold);if(p.code.isNotBlank())Text("ATC: ${p.code}",style=MaterialTheme.typography.labelSmall,color=Color.Gray)}
       OutlinedTextField(value=quantities[p.row]?:"",onValueChange={v->quantities=quantities.toMutableMap().apply{if(v.isBlank())remove(p.row)else put(p.row,v)}},label={Text("العدد",fontSize=11.sp)},modifier=Modifier.width(84.dp),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
       TextButton(onClick={favorites=if(favorites.contains(p.row.toString()))favorites-p.row.toString()else favorites+p.row.toString()},contentPadding=PaddingValues(horizontal=2.dp)){Text(if(favorites.contains(p.row.toString()))"★"else"☆",color=Orange,fontSize=21.sp)}
      }
     }
    }
   }
   Text(status,fontSize=11.sp,color=Color.DarkGray,maxLines=2)
   Button(onClick={exportAndShare()},modifier=Modifier.fillMaxWidth().height(50.dp),shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(containerColor=Burgundy)){Text("حفظ وإرسال ملف Excel",fontWeight=FontWeight.Bold)}
  }
 }
 if(showHelp)AlertDialog(onDismissRequest={showHelp=false},title={Text("عن التطبيق والتعليمات")},text={Text("تطبيق محلي لإعداد تقرير وحدة السيطرة على مقاومة مضادات المايكروبات. اختر السنة والشهر، أدخل أعداد السكان، ثم أدخل الكمية المصروفة أمام كل مضاد. يمكنك البحث أو فتح قائمة الاختيار، وإضافة الأصناف المستخدمة إلى المفضلة بالنجمة. العنابي للجرعة/التركيز والبرتقالي للشكل الدوائي. زر حفظ وإرسال ينشئ نسخة Excel ويحفظها داخل التطبيق ثم يفتح قائمة المشاركة.\n\nتم التصميم بواسطة الصيدلاني محمد رحيم.")},confirmButton={TextButton(onClick={showHelp=false}){Text("إغلاق")}})
}

@Composable private fun Badge(text:String,color:Color,dark:Boolean=false){Box(Modifier.background(color,RoundedCornerShape(5.dp)).padding(horizontal=6.dp,vertical=4.dp),contentAlignment=Alignment.Center){Text(text,fontSize=10.sp,maxLines=2,color=if(dark)Color(0xFF3D2512)else Color.White,fontWeight=FontWeight.Bold)}}
