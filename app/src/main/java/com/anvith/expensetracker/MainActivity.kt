package com.anvith.expensetracker

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private data class Expense(
    val id: Long,
    val title: String,
    val category: String,
    val amount: Double,
    val payment: String,
    val person: String,
    val paid: Boolean,
    val time: Long
)

private const val PREFS = "expense_tracker"

private fun money(v: Double) = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(v)

private fun load(c: Context): List<Expense> {
    val s = c.getSharedPreferences(PREFS, 0).getString("expenses", null) ?: return emptyList()
    return runCatching {
        val a = JSONArray(s)
        buildList {
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                add(
                    Expense(
                        id = o.getLong("id"),
                        title = o.getString("title"),
                        category = o.getString("category"),
                        amount = o.getDouble("amount"),
                        payment = o.getString("payment"),
                        person = o.optString("person", ""),
                        paid = o.optBoolean("paid", false),
                        time = o.getLong("time")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun save(c: Context, x: List<Expense>) {
    val a = JSONArray()
    x.forEach { e ->
        a.put(
            JSONObject().apply {
                put("id", e.id)
                put("title", e.title)
                put("category", e.category)
                put("amount", e.amount)
                put("payment", e.payment)
                put("person", e.person)
                put("paid", e.paid)
                put("time", e.time)
            }
        )
    }
    c.getSharedPreferences(PREFS, 0).edit().putString("expenses", a.toString()).apply()
}

private fun getBudget(c: Context) = c.getSharedPreferences(PREFS, 0).getFloat("budget", 15000f).toDouble()
private fun setBudget(c: Context, v: Double) = c.getSharedPreferences(PREFS, 0).edit().putFloat("budget", v.toFloat()).apply()
private fun backupUrl(c: Context) = c.getSharedPreferences(PREFS, 0).getString("backup_url", "") ?: ""
private fun backupToken(c: Context) = c.getSharedPreferences(PREFS, 0).getString("backup_token", "") ?: ""
private fun autoBackup(c: Context) = c.getSharedPreferences(PREFS, 0).getBoolean("auto_backup", false)
private fun setBackup(c: Context, url: String, token: String, enabled: Boolean) =
    c.getSharedPreferences(PREFS, 0).edit().putString("backup_url", url.trim()).putString("backup_token", token.trim()).putBoolean("auto_backup", enabled).apply()
private fun dateText(time: Long) = SimpleDateFormat("dd MMM", Locale.ENGLISH).format(Date(time))

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { App(applicationContext) }
    }
}

@Composable
private fun App(c: Context) {
    var data by remember { mutableStateOf(load(c)) }
    var budget by remember { mutableStateOf(getBudget(c)) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var add by rememberSaveable { mutableStateOf(false) }
    var budgetDlg by rememberSaveable { mutableStateOf(false) }
    var dark by rememberSaveable { mutableStateOf(false) }

    fun commit(v: List<Expense>) {
        data = v
        save(c, v)
    }

    fun markPaid(id: Long) {
        commit(data.map { if (it.id == id) it.copy(paid = true) else it })
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf(
                        "Home" to Icons.Default.Home,
                        "History" to Icons.Default.List,
                        "Insights" to Icons.Default.Analytics,
                        "Settings" to Icons.Default.Settings
                    ).forEachIndexed { i, (n, ic) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(ic, n) },
                            label = { Text(n) }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (tab < 3) FloatingActionButton(onClick = { add = true }) {
                    Icon(Icons.Default.Add, "Add")
                }
            }
        ) { p ->
            when (tab) {
                0 -> Home(data, budget, p) { budgetDlg = true }
                1 -> History(data, p, ::markPaid) { id -> commit(data.filterNot { it.id == id }) }
                2 -> Insights(data, p)
                3 -> Settings(c, dark, p) { dark = it }
            }
        }

        if (add) AddDialog({ add = false }) { e ->
            commit(listOf(e) + data)
            add = false
        }
        if (budgetDlg) BudgetDialog(budget, { budgetDlg = false }) {
            budget = it
            setBudget(c, it)
            budgetDlg = false
        }
    }
}

@Composable
private fun Home(x: List<Expense>, budget: Double, p: PaddingValues, onBudget: () -> Unit) {
    val total = x.sumOf { it.amount }
    val owed = x.filterNot { it.paid }
    val owedTotal = owed.sumOf { it.amount }
    val left = (budget - total).coerceAtLeast(0.0)
    val pct = if (budget <= 0) 0f else (total / budget).coerceIn(0.0, 1.0).toFloat()
    val cats = x.groupBy { it.category }
        .mapValues { it.value.sumOf { e -> e.amount } }
        .toList()
        .sortedByDescending { it.second }

    LazyColumn(
        Modifier.fillMaxSize().padding(p).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Expense Tracker", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Your money, under control", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onBudget) { Text("Budget") }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Spent this month", style = MaterialTheme.typography.labelLarge)
                    Text(money(total), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${money(left)} remaining of ${money(budget)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Expenses", x.size.toString(), Modifier.weight(1f))
                Stat("Average", money(if (x.isEmpty()) 0.0 else total / x.size), Modifier.weight(1f))
            }
        }
        if (owed.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Still owed", fontWeight = FontWeight.Bold)
                        Text(money(owedTotal), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${owed.size} unpaid expense${if (owed.size == 1) "" else "s"}")
                    }
                }
            }
        }
        item { Text("Spending by category", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(cats.take(5)) { (n, v) -> Cat(n, v, if (total == 0.0) 0f else (v / total).toFloat()) }
        item { Text("Recent", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(x.take(5), key = { it.id }) { ExpenseRow(it) }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun Stat(t: String, v: String, m: Modifier) {
    Card(modifier = m) {
        Column(Modifier.padding(16.dp)) {
            Text(t, style = MaterialTheme.typography.labelMedium)
            Text(v, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun Cat(n: String, v: Double, s: Float) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(n, fontWeight = FontWeight.SemiBold)
            Text(money(v), fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(progress = { s }, modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
    }
}

@Composable
private fun ExpenseRow(e: Expense) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (e.paid) Icons.Default.CheckCircle else Icons.Default.AttachMoney, null)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, fontWeight = FontWeight.SemiBold)
            val who = if (e.person.isBlank()) "No person" else "Owe ${e.person}"
            val status = if (e.paid) "Paid" else who
            Text(
                "${e.category} • ${e.payment} • ${dateText(e.time)} • $status",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(money(e.amount), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun History(
    x: List<Expense>,
    p: PaddingValues,
    onPaid: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    var q by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("All") }
    val cs = listOf("All", "Food", "Shopping", "Transport", "Bills")
    val filtered = x.filter {
        (q.isBlank() || it.title.contains(q, true) || it.person.contains(q, true)) &&
            (cat == "All" || it.category == cat)
    }
    val pending = filtered.filterNot { it.paid }
    val paid = filtered.filter { it.paid }

    Column(Modifier.fillMaxSize().padding(p).padding(20.dp)) {
        Text("History", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            q,
            { q = it },
            Modifier.fillMaxWidth(),
            placeholder = { Text("Search expense or person") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 10.dp)
        ) {
            cs.forEach { f ->
                FilterChip(cat == f, { cat = f }, label = { Text(f) })
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Owed / Pending (${pending.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            items(pending, key = { it.id }) { e ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        ExpenseRow(e)
                        if (e.person.isNotBlank()) {
                            Text("Owed to ${e.person}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onDelete(e.id) }) { Text("Delete") }
                            Button(onClick = { onPaid(e.id) }) {
                                Icon(Icons.Default.Check, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Paid")
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("Paid", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (paid.isEmpty()) {
                item {
                    Text("No paid expenses yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(paid, key = { it.id }) { e ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            ExpenseRow(e)
                            if (e.person.isNotBlank()) {
                                Text("Paid to ${e.person}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { onDelete(e.id) }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun Insights(x: List<Expense>, p: PaddingValues) {
    val total = x.sumOf { it.amount }
    val pending = x.filterNot { it.paid }
    val paid = x.filter { it.paid }
    val g = x.groupBy { it.category }
        .mapValues { it.value.sumOf { e -> e.amount } }
        .toList()
        .sortedByDescending { it.second }

    LazyColumn(
        Modifier.fillMaxSize().padding(p).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Insights", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold) }
        item {
            Card {
                Column(Modifier.padding(18.dp)) {
                    Text("Total tracked")
                    Text(money(total), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${x.size} expenses • ${pending.size} unpaid • ${paid.size} paid")
                }
            }
        }
        item { Text("Where your money goes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(g) { (n, v) -> Cat(n, v, if (total == 0.0) 0f else (v / total).toFloat()) }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun Settings(c: Context, dark: Boolean, p: PaddingValues, onDark: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().padding(p).padding(20.dp)) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(16.dp))
        Card {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Dark mode", fontWeight = FontWeight.Bold)
                Switch(dark, onDark)
            }
        }
        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.height(20.dp))
        Text("Automatic laptop backup", fontWeight = FontWeight.Bold)
        Text("Copies your expenses to your Windows laptop over local Wi-Fi. No cloud account is used.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        var url by rememberSaveable { mutableStateOf(backupUrl(c)) }
        var token by rememberSaveable { mutableStateOf(backupToken(c)) }
        var enabled by rememberSaveable { mutableStateOf(autoBackup(c)) }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(url, { url = it }, label = { Text("Laptop backup URL") }, placeholder = { Text("http://192.168.1.10:8765/backup") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(token, { token = it }, label = { Text("Backup token") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Automatic backup")
            Switch(enabled, { enabled = it; setBackup(c, url, token, it); BackupScheduler.schedule(c) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { setBackup(c, url, token, enabled); BackupScheduler.schedule(c) }) { Text("Save") }
            OutlinedButton(onClick = { setBackup(c, url, token, enabled); Thread {
                val ok = BackupWorker.runNow(c)
                android.os.Handler(c.mainLooper).post { Toast.makeText(c, if (ok) "Backup complete" else "Backup failed — check laptop", Toast.LENGTH_LONG).show() }
            }.start() }) { Text("Backup now") }
        }
        Spacer(Modifier.height(18.dp))
        Text("Privacy", fontWeight = FontWeight.Bold)
        Text("Your data stays on this phone and is copied only to the Windows backup server you configure.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddDialog(dismiss: () -> Unit, saveExpense: (Expense) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var person by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("Food") }
    var pay by rememberSaveable { mutableStateOf("UPI") }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Add expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    person,
                    { person = it },
                    label = { Text("Whom do you owe it to? (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Food", "Shopping", "Transport", "Bills").forEach {
                        FilterChip(cat == it, { cat = it }, label = { Text(it) })
                    }
                }
                Text("Payment", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("UPI", "Card", "Cash").forEach {
                        FilterChip(pay == it, { pay = it }, label = { Text(it) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val v = amount.toDoubleOrNull()
                if (v != null && v > 0 && title.isNotBlank()) {
                    val now = System.currentTimeMillis()
                    saveExpense(
                        Expense(
                            id = now,
                            title = title.trim(),
                            category = cat,
                            amount = v,
                            payment = pay,
                            person = person.trim(),
                            paid = false,
                            time = now
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(dismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun BudgetDialog(cur: Double, dismiss: () -> Unit, saveBudget: (Double) -> Unit) {
    var t by rememberSaveable { mutableStateOf(cur.toString()) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Monthly budget") },
        text = {
            OutlinedTextField(
                t,
                { t = it },
                label = { Text("Budget (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { t.toDoubleOrNull()?.takeIf { it > 0 }?.let(saveBudget) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(dismiss) { Text("Cancel") }
        }
    )
}
