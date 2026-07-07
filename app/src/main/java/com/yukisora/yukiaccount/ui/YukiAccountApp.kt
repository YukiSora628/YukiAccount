package com.yukisora.yukiaccount.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yukisora.yukiaccount.domain.model.Account
import com.yukisora.yukiaccount.domain.model.AccountType
import com.yukisora.yukiaccount.domain.model.InvestmentAsset
import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.Transaction
import com.yukisora.yukiaccount.domain.model.TransactionType
import com.yukisora.yukiaccount.domain.service.LedgerCalculator

private enum class AppTab(val title: String) {
    DASHBOARD("首页"),
    TRANSACTIONS("流水"),
    ACCOUNTS("账户"),
    INVESTMENTS("投资"),
    SETTINGS("设置"),
}

private enum class EntryDialog {
    EXPENSE,
    INCOME,
    INVESTMENT_BUY,
    VALUATION,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YukiAccountApp() {
    var selectedTab by remember { mutableStateOf(AppTab.DASHBOARD) }
    var state by remember { mutableStateOf(AppState()) }
    var dialog by remember { mutableStateOf<EntryDialog?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(tab.title.first().toString()) },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { padding ->
        when (selectedTab) {
            AppTab.DASHBOARD -> DashboardScreen(
                state = state,
                padding = padding,
                onOpenDialog = { dialog = it },
            )
            AppTab.TRANSACTIONS -> TransactionListScreen(state.transactions, padding)
            AppTab.ACCOUNTS -> AccountListScreen(state.accounts, padding)
            AppTab.INVESTMENTS -> InvestmentListScreen(state.investments, padding)
            AppTab.SETTINGS -> SettingsScreen(padding)
        }
    }

    when (dialog) {
        EntryDialog.EXPENSE -> MoneyEntryDialog(
            title = "记一笔支出",
            accounts = state.accounts,
            confirmText = "保存支出",
            onDismiss = { dialog = null },
            onConfirm = { amount, account, note ->
                state = state.addExpense(amount, account.id, note)
                dialog = null
            },
        )
        EntryDialog.INCOME -> MoneyEntryDialog(
            title = "记一笔收入",
            accounts = state.accounts.filter { it.type != AccountType.CREDIT_CARD },
            confirmText = "保存收入",
            onDismiss = { dialog = null },
            onConfirm = { amount, account, note ->
                state = state.addIncome(amount, account.id, note)
                dialog = null
            },
        )
        EntryDialog.INVESTMENT_BUY -> InvestmentBuyDialog(
            accounts = state.accounts.filter { it.type != AccountType.CREDIT_CARD },
            investments = state.investments,
            onDismiss = { dialog = null },
            onConfirm = { amount, account, investment, note ->
                state = state.addInvestmentBuy(amount, account.id, investment.id, note)
                dialog = null
            },
        )
        EntryDialog.VALUATION -> ValuationDialog(
            investments = state.investments,
            onDismiss = { dialog = null },
            onConfirm = { investment, value ->
                state = state.updateInvestmentValue(investment.id, value)
                dialog = null
            },
        )
        null -> Unit
    }
}

@Composable
private fun DashboardScreen(
    state: AppState,
    padding: PaddingValues,
    onOpenDialog: (EntryDialog) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "YukiAccount",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(
                    title = "本月真实消费",
                    value = state.monthlyConsumption.formatCurrency(),
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = "当前净资产",
                    value = state.netWorth.formatCurrency(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("快捷操作", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onOpenDialog(EntryDialog.EXPENSE) },
                        modifier = Modifier.weight(1f),
                    ) { Text("记支出") }
                    Button(
                        onClick = { onOpenDialog(EntryDialog.INCOME) },
                        modifier = Modifier.weight(1f),
                    ) { Text("记收入") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onOpenDialog(EntryDialog.INVESTMENT_BUY) },
                        modifier = Modifier.weight(1f),
                    ) { Text("投资买入") }
                    Button(
                        onClick = { onOpenDialog(EntryDialog.VALUATION) },
                        modifier = Modifier.weight(1f),
                    ) { Text("更新市值") }
                }
            }
        }
        item {
            InfoCard(
                title = "自动周期账单",
                body = "打开 App 时会自动补记会员订阅、自动续费和定投。当前没有待补记项目。",
            )
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TransactionListScreen(transactions: List<Transaction>, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("流水", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        if (transactions.isEmpty()) {
            item { InfoCard(title = "暂无流水", body = "从首页快捷操作录入第一笔流水。") }
        } else {
            transactions.forEach { transaction ->
                item {
                    InfoCard(
                        title = "${transaction.type.label()} ${transaction.amount.formatCurrency()}",
                        body = "${transaction.date} ${transaction.note.ifBlank { "无备注" }}",
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountListScreen(accounts: List<Account>, padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
    ) {
        Text("账户", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            accounts.forEach { account ->
                InfoCard(
                    title = account.name,
                    body = if (account.type == AccountType.CREDIT_CARD) {
                        "未还负债 ${account.balance.formatCurrency()}"
                    } else {
                        "余额 ${account.balance.formatCurrency()}"
                    },
                )
            }
        }
    }
}

@Composable
private fun InvestmentListScreen(investments: List<InvestmentAsset>, padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
    ) {
        Text("投资", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            investments.forEach { investment ->
                val gainLoss = LedgerCalculator.investmentGainLoss(investment)
                InfoCard(
                    title = investment.name,
                    body = "本金 ${investment.principal.formatCurrency()} / 市值 ${investment.currentValue.formatCurrency()} / 浮盈浮亏 ${gainLoss.formatCurrency()}",
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        item { InfoCard("分类管理", "管理消费、收入、固定支出和投资投入分类。") }
        item { InfoCard("周期规则", "管理会员订阅、自动续费和定投规则。") }
        item { InfoCard("导入导出", "手动导出或导入本地 JSON 备份。") }
    }
}

@Composable
private fun MoneyEntryDialog(
    title: String,
    accounts: List<Account>,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (Money, Account, String) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedAccount by remember(accounts) { mutableStateOf(accounts.first()) }
    val amount = amountText.toMoneyOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("金额") },
                    singleLine = true,
                )
                AccountSelector(
                    accounts = accounts,
                    selected = selectedAccount,
                    onSelected = { selectedAccount = it },
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null,
                onClick = { onConfirm(requireNotNull(amount), selectedAccount, note) },
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun InvestmentBuyDialog(
    accounts: List<Account>,
    investments: List<InvestmentAsset>,
    onDismiss: () -> Unit,
    onConfirm: (Money, Account, InvestmentAsset, String) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedAccount by remember(accounts) { mutableStateOf(accounts.first()) }
    var selectedInvestment by remember(investments) { mutableStateOf(investments.first()) }
    val amount = amountText.toMoneyOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("投资买入") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("金额") })
                AccountSelector(accounts = accounts, selected = selectedAccount, onSelected = { selectedAccount = it })
                InvestmentSelector(
                    investments = investments,
                    selected = selectedInvestment,
                    onSelected = { selectedInvestment = it },
                )
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null,
                onClick = { onConfirm(requireNotNull(amount), selectedAccount, selectedInvestment, note) },
            ) { Text("保存买入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ValuationDialog(
    investments: List<InvestmentAsset>,
    onDismiss: () -> Unit,
    onConfirm: (InvestmentAsset, Money) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var selectedInvestment by remember(investments) { mutableStateOf(investments.first()) }
    val amount = amountText.toMoneyOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新投资市值") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InvestmentSelector(
                    investments = investments,
                    selected = selectedInvestment,
                    onSelected = { selectedInvestment = it },
                )
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("当前市值") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null,
                onClick = { onConfirm(selectedInvestment, requireNotNull(amount)) },
            ) { Text("更新") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSelector(
    accounts: List<Account>,
    selected: Account,
    onSelected: (Account) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("账户") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = {
                        onSelected(account)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvestmentSelector(
    investments: List<InvestmentAsset>,
    selected: InvestmentAsset,
    onSelected: (InvestmentAsset) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("投资资产") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            investments.forEach { investment ->
                DropdownMenuItem(
                    text = { Text(investment.name) },
                    onClick = {
                        onSelected(investment)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun TransactionType.label(): String =
    when (this) {
        TransactionType.EXPENSE -> "支出"
        TransactionType.INCOME -> "收入"
        TransactionType.REFUND -> "退款"
        TransactionType.TRANSFER -> "转账"
        TransactionType.CREDIT_CARD_REPAYMENT -> "信用卡还款"
        TransactionType.INVESTMENT_BUY -> "投资买入"
    }
