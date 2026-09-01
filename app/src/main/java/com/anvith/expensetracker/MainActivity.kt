package com.anvith.expensetracker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREFS = "expense_tracker"
private const val KEY_EXPENSES = "expenses"
private const val KEY_BUDGET = "budget"
private const val KEY_DARK = "dark"

private data class Expense(
    val id: Long,
    val title: String,
    val category: String,
    val amount: Double,
    val payment: String,
    val timestamp: Long
)

private object Store {
    fun load(context: Context): List<Expense> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EXPENSES, null) ?: return listOf(
            Expense(1, "Dinner", "Food", 420.0, "UPI", System.currentTimeMillis()),
            Expense(2, "Metro", "Transport", 80.0, "Card", System.currentTimeMillis()),
            Expense(3, "Groceries", "Shopping", 1260.0, "UPI", System.currentTimeMillis()),
            Expense(4, "Coffee", "Food", 180.0, "Cash", System.currentTimeMillis())
        )
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(Expense(o.getLong("id"), o.getString("title"), o.getString("category"), o.getDouble("amount"), o.getString("payment"), o.getLong("timestamp")))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, expenses: List<Expense>) {
        val array = JSONArray()
        expenses.forEach { e ->
            array.put(JSONObject().apply {
                put("id", e.id); put("title", e.title); put("category", e.category)
                put("amount", e.amount); put("payment", e.payment); put("timestamp", e.timestamp)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_EXPENSES, array.toString()).apply()
    }

    fun budget(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(KEY_BUDGET, 15000f).toDouble()
    fun setBudget(context: Context, value: Double) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY_BUDGET, value.toFloat()).apply()
    fun dark(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DARK, false)
    fun setDark(context: Context, value: Boolean) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DARK, value).apply()
}

private fun money(value: Double) = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
private fun dateText(time: Long) = SimpleDateFormat("dd MMM", Locale.ENGLISH).format(Date(time))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ExpenseTrackerApp(applicationContext) }
    }
}

@Composable
private fun ExpenseTrackerApp(context: Context) {
    var expenses by remember { mutableStateOf(Store.load(context)) }
    var budget by remember { mutableStateOf(Store.budget(context)) }
    var dark by rememberSaveable { mutableStateOf(Store.dark(context)) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var budgetOpen by rememberSaveable { mutableStateOf(false) }

    fun save(list: List<Expense>) { expenses = list; Store.save(context, list) }

    MaterialTheme(colorScheme = if (dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("Home" to Icons.Default.Home, "History" to Icons.Default.Search, "Insights" to Icons.Default.Analytics, "Settings" to Icons.Default.Settings).forEachIndexed { i, item ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(item.second, null) }, label = { Text(item.first) })
                    }
                }
            },
            floatingActionButton = { if (tab < 3) FloatingActionButton(onClick = { addOpen = true }) { Icon(Icons.Default.Add, "Add") } }
        ) { padding ->
            when (tab) {
                0 -> HomeScreen(expenses, budget, padding, { budgetOpen = true })
                1 -> HistoryScreen(expenses, padding) { id -> save(expenses.filterNot { it.id == id }) }
                2 -> InsightsScreen(expenses, padding)
                else -> SettingsScreen(dark, { dark = it; Store.setDark(context, it) }, padding)
            }
        }
        if (addOpen) AddExpenseDialog({ addOpen = false }) { e -> save(listOf(e) + expenses); addOpen = false }
        if (budgetOpen) BudgetDialog(budget, { budgetOpen = false }) { value -> budget = value; Store.setBudget(context, value); budgetOpen = false }
    }
}

@Composable
private fun HomeScreen(expenses: List<Expense>, budget: Double, padding: PaddingValues, onBudget: () -> Unit) {
    val total = expenses.sumOf { it.amount }
    val pct = if (budget <= 0) 0f else (total / budget).coerceIn(0.0, 1.0).toFloat()
    val cats = expenses.groupBy { it.category }.mapValues { it.value.sumOf(Expense::amount) }.toList().sortedByDescending { it.second }
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text("Expense Tracker", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold); Text("Your money, under control", color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton(onClick = onBudget) { Text("Budget") } } }
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Text("Spent this month", style = MaterialTheme.typography.labelLarge)
                    Text(money(total), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${money((budget - total).coerceAtLeast(0.0))} remaining of ${money(budget)}", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .72f))
                    Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress = { pct }, Modifier.fillMaxWidth())
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { MetricCard("Expenses", "${expenses.size}", Icons.Default.Category, Modifier.weight(1f)); MetricCard("Daily avg", money(if (expenses.isEmpty()) 0.0 else total / maxOf(1, expenses.size)), Icons.Default.Analytics, Modifier.weight(1f)) } }
        item { Text("Spending by category", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(cats.take(5)) { (name, value) -> CategoryRow(name, value, if (total == 0.0) 0f else (value / total).toFloat()) }
        item { Text("Recent", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(expenses.take(5), key = { it.id }) { ExpenseRow(it) }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable private fun MetricCard(title: String, value: String, icon: ImageVector, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) { Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.height(10.dp)); Text(title, style = MaterialTheme.typography.labelMedium); Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp) } }
}

@Composable private fun CategoryRow(name: String, value: Double, share: Float) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Category, null) }
        Spacer(Modifier.size(12.dp)); Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(name, fontWeight = FontWeight.SemiBold); Text(money(value), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(5.dp)); LinearProgressIndicator(progress = { share }, Modifier.fillMaxWidth())
        }
    }
}

@Composable private fun ExpenseRow(e: Expense) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Category, null) }
        Spacer(Modifier.size(12.dp)); Column(Modifier.weight(1f)) { Text(e.title, fontWeight = FontWeight.SemiBold); Text("${e.category} • ${e.payment} • ${dateText(e.timestamp)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(money(e.amount), fontWeight = FontWeight.Bold)
    }
}

@Composable private fun HistoryScreen(expenses: List<Expense>, padding: PaddingValues, onDelete: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    val filters = listOf("All", "Food", "Shopping", "Transport", "Bills")
    val shown = expenses.filter { (query.isBlank() || it.title.contains(query, true)) && (category == "All" || it.category == category) }
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
        Text("History", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, Modifier.padding(top = 8.dp, bottom = 12.dp))
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search expenses") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { filters.forEach { f -> FilterChip(category == f, { category = f }, label = { Text(f) }) } }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(shown, key = { it.id }) { e -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { ExpenseRow(e); IconButton({ onDelete(e.id) }) { Icon(Icons.Default.DeleteOutline, "Delete") } } }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable private fun InsightsScreen(expenses: List<Expense>, padding: PaddingValues) {
    val total = expenses.sumOf { it.amount }
    val groups = expenses.groupBy { it.category }.mapValues { it.value.sumOf(Expense::amount) }.toList().sortedByDescending { it.second }
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Insights", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, Modifier.padding(top = 8.dp)) }
        item { Card(shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(20.dp)) { Text("Total tracked", style = MaterialTheme.typography.labelLarge); Text(money(total), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold); Text("${expenses.size} expenses recorded", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { Text("Where your money goes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(groups) { (name, value) -> CategoryRow(name, value, if (total == 0.0) 0f else (value / total).toFloat()) }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable private fun SettingsScreen(dark: Boolean, onDark: (Boolean) -> Unit, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(22.dp)) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("Dark mode", fontWeight = FontWeight.Bold); Text("Use a darker appearance", color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(dark, onDark) } }
        Spacer(Modifier.height(14.dp)); Text("Privacy", fontWeight = FontWeight.Bold); Text("Expenses are stored locally on this device. The app does not require an account or network connection.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable private fun AddExpenseDialog(onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }; var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Food") }; var payment by rememberSaveable { mutableStateOf("UPI") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add expense") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(title, { title = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Food", "Shopping", "Transport", "Bills").forEach { c -> FilterChip(category == c, { category = c }, label = { Text(c) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("UPI", "Card", "Cash").forEach { p -> FilterChip(payment == p, { payment = p }, label = { Text(p) }) } }
        }
    }, confirmButton = { Button(onClick = { val v = amount.toDoubleOrNull(); if (v != null && v > 0 && title.isNotBlank()) onSave(Expense(System.currentTimeMillis(), title.trim(), category, v, payment, System.currentTimeMillis())) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun BudgetDialog(current: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var value by rememberSaveable { mutableStateOf(current.toInt().toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Monthly budget") }, text = { OutlinedTextField(value, { value = it }, label = { Text("Budget (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }, confirmButton = { Button(onClick = { value.toDoubleOrNull()?.takeIf { it > 0 }?.let(onSave) ?: onDismiss() }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
