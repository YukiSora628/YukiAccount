package com.yukisora.yukiaccount.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yukisora.yukiaccount.data.backup.BackupImportResult
import com.yukisora.yukiaccount.domain.model.Account
import com.yukisora.yukiaccount.domain.model.AccountType
import com.yukisora.yukiaccount.domain.model.Category
import com.yukisora.yukiaccount.domain.model.InvestmentAsset
import com.yukisora.yukiaccount.domain.model.InvestmentType
import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.RecurringFrequency
import com.yukisora.yukiaccount.domain.model.RecurringRule
import com.yukisora.yukiaccount.domain.model.Transaction
import com.yukisora.yukiaccount.domain.model.TransactionType
import com.yukisora.yukiaccount.domain.model.ValuationSnapshot
import com.yukisora.yukiaccount.domain.service.LedgerCalculator
import com.yukisora.yukiaccount.domain.service.TransactionFilter
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

private enum class AppTab(val title: String, val icon: ImageVector) {
    DASHBOARD("首页", Icons.Filled.Home),
    TRANSACTIONS("流水", Icons.AutoMirrored.Filled.List),
    ACCOUNTS("账户", Icons.Filled.AccountCircle),
    INVESTMENTS("投资", Icons.Filled.Star),
    SETTINGS("设置", Icons.Filled.Settings),
}

private enum class EntryDialog {
    EXPENSE,
    INCOME,
    REFUND,
    TRANSFER,
    CREDIT_CARD_REPAYMENT,
    INVESTMENT_BUY,
    VALUATION,
    RECURRING_RULE,
    ACCOUNT,
    INVESTMENT_ASSET,
    CATEGORY,
    RESET_LOCAL_DATA,
}

private val requiredSystemCategoryIds = setOf("subscription", "investment-input")
private val categoryTypes = listOf("expense", "income", "transfer", "investment")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YukiAccountApp(viewModel: AppViewModel = viewModel()) {
    var selectedTab by remember { mutableStateOf(AppTab.DASHBOARD) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<EntryDialog?>(null) }
    var localStatusMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val json = viewModel.exportBackupJson()
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: error("无法写入备份文件")
                    stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                        writer.write(json)
                    }
                }
                localStatusMessage = result.fold(
                    onSuccess = { "备份已导出" },
                    onFailure = { "导出失败：${it.message ?: "未知错误"}" },
                )
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: error("无法读取备份文件")
                    val rawJson = stream.bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.readText()
                    }
                    viewModel.importBackupJson(rawJson)
                }
                localStatusMessage = result.fold(
                    onSuccess = { importResult ->
                        when (importResult) {
                            is BackupImportResult.Valid -> "备份已导入"
                            is BackupImportResult.Invalid -> "导入失败：${importResult.reason}"
                        }
                    },
                    onFailure = { "导入失败：${it.message ?: "未知错误"}" },
                )
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                            )
                        },
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
                onUndoRecurringGeneration = viewModel::undoLastRecurringGeneration,
            )
            AppTab.TRANSACTIONS -> TransactionListScreen(
                transactions = state.transactions,
                categories = state.allCategories,
                accounts = state.allAccounts,
                investments = state.allInvestments,
                padding = padding,
            )
            AppTab.ACCOUNTS -> AccountListScreen(
                accounts = state.accounts,
                transactions = state.transactions,
                padding = padding,
                onCreateAccount = { dialog = EntryDialog.ACCOUNT },
                onArchiveAccount = viewModel::archiveAccount,
            )
            AppTab.INVESTMENTS -> InvestmentListScreen(
                investments = state.investments,
                valuations = state.valuations,
                padding = padding,
                onCreateInvestment = { dialog = EntryDialog.INVESTMENT_ASSET },
                onUpdateValuation = { dialog = EntryDialog.VALUATION },
                onArchiveInvestment = viewModel::archiveInvestmentAsset,
            )
            AppTab.SETTINGS -> SettingsScreen(
                padding = padding,
                categories = state.categories,
                recurringRules = state.recurringRules,
                onExport = {
                    exportLauncher.launch("yuki-account-backup-${LocalDate.now()}.json")
                },
                onImport = {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                onCreateCategory = { dialog = EntryDialog.CATEGORY },
                onArchiveCategory = viewModel::archiveCategory,
                onCreateRecurringRule = { dialog = EntryDialog.RECURRING_RULE },
                onSkipNextOccurrence = viewModel::skipNextRecurringOccurrence,
                onSetRecurringRuleEnabled = viewModel::setRecurringRuleEnabled,
                onResetLocalData = { dialog = EntryDialog.RESET_LOCAL_DATA },
            )
        }
    }

    when (dialog) {
        EntryDialog.EXPENSE -> MoneyEntryDialog(
            title = "记一笔支出",
            accounts = state.accounts,
            categories = state.categories.filter { it.type == "expense" },
            confirmText = "保存支出",
            onDismiss = { dialog = null },
            onConfirm = { amount, account, category, date, note ->
                viewModel.addExpense(amount, account, category, date, note)
                dialog = null
            },
        )
        EntryDialog.INCOME -> MoneyEntryDialog(
            title = "记一笔收入",
            accounts = state.accounts.filter { it.type != AccountType.CREDIT_CARD },
            categories = state.categories.filter { it.type == "income" },
            confirmText = "保存收入",
            onDismiss = { dialog = null },
            onConfirm = { amount, account, category, date, note ->
                viewModel.addIncome(amount, account, category, date, note)
                dialog = null
            },
        )
        EntryDialog.REFUND -> MoneyEntryDialog(
            title = "记一笔退款",
            accounts = state.accounts,
            categories = state.categories.filter { it.type == "expense" },
            confirmText = "保存退款",
            onDismiss = { dialog = null },
            onConfirm = { amount, account, category, date, note ->
                viewModel.addRefund(amount, account, category, date, note)
                dialog = null
            },
        )
        EntryDialog.TRANSFER -> TransferDialog(
            accounts = state.accounts.filter { it.type != AccountType.CREDIT_CARD },
            categories = state.categories.filter { it.type == "transfer" },
            onDismiss = { dialog = null },
            onConfirm = { amount, sourceAccount, targetAccount, category, date, note ->
                viewModel.addTransfer(amount, sourceAccount, targetAccount, category, date, note)
                dialog = null
            },
        )
        EntryDialog.INVESTMENT_BUY -> InvestmentBuyDialog(
            accounts = state.accounts.filter { it.type != AccountType.CREDIT_CARD },
            investments = state.investments,
            onDismiss = { dialog = null },
            onConfirm = { amount, account, investment, date, note ->
                viewModel.addInvestmentBuy(amount, account, investment, date, note)
                dialog = null
            },
        )
        EntryDialog.CREDIT_CARD_REPAYMENT -> CreditCardRepaymentDialog(
            sourceAccounts = state.accounts.filter { it.type != AccountType.CREDIT_CARD },
            creditCards = state.accounts.filter { it.type == AccountType.CREDIT_CARD },
            onDismiss = { dialog = null },
            onConfirm = { amount, sourceAccount, creditCard, date, note ->
                viewModel.addCreditCardRepayment(amount, sourceAccount, creditCard, date, note)
                dialog = null
            },
        )
        EntryDialog.RECURRING_RULE -> RecurringRuleDialog(
            accounts = state.accounts.filter { it.type != AccountType.CREDIT_CARD },
            investments = state.investments,
            onDismiss = { dialog = null },
            onConfirmSubscription = { name, amount, account, frequency, startDate, endDate ->
                viewModel.addSubscriptionRule(name, amount, account, frequency, startDate, endDate)
                dialog = null
            },
            onConfirmInvestmentBuy = { name, amount, account, investment, frequency, startDate, endDate ->
                viewModel.addInvestmentBuyRule(name, amount, account, investment, frequency, startDate, endDate)
                dialog = null
            },
        )
        EntryDialog.ACCOUNT -> AccountDialog(
            onDismiss = { dialog = null },
            onConfirmAsset = { name, type, balance ->
                viewModel.addAssetAccount(name, type, balance)
                dialog = null
            },
            onConfirmCreditCard = { name, unpaidBalance, creditLimit, billingDay, repaymentDay ->
                viewModel.addCreditCardAccount(name, unpaidBalance, creditLimit, billingDay, repaymentDay)
                dialog = null
            },
        )
        EntryDialog.INVESTMENT_ASSET -> InvestmentAssetDialog(
            onDismiss = { dialog = null },
            onConfirm = { name, type, principal, currentValue ->
                viewModel.addInvestmentAsset(name, type, principal, currentValue)
                dialog = null
            },
        )
        EntryDialog.VALUATION -> ValuationDialog(
            investments = state.investments,
            onDismiss = { dialog = null },
            onConfirm = { investment, value, date ->
                viewModel.updateInvestmentValue(investment, value, date)
                dialog = null
            },
        )
        EntryDialog.CATEGORY -> CategoryDialog(
            onDismiss = { dialog = null },
            onConfirm = { name, type, isFixedExpense ->
                viewModel.addCategory(name, type, isFixedExpense)
                dialog = null
            },
        )
        EntryDialog.RESET_LOCAL_DATA -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("重置本地数据") },
            text = { Text("这会永久删除全部账户、流水、周期规则和投资记录，并恢复默认数据。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetLocalData()
                        dialog = null
                    },
                    modifier = Modifier.testTag("confirm-reset-local-data"),
                ) {
                    Text("确认重置")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) {
                    Text("取消")
                }
            },
        )
        null -> Unit
    }

    val visibleStatusMessage = localStatusMessage ?: state.statusMessage
    visibleStatusMessage?.let { message ->
        SimpleMessageDialog(
            title = if (localStatusMessage != null) "导入导出" else "操作提示",
            message = message,
            onDismiss = {
                if (localStatusMessage != null) {
                    localStatusMessage = null
                } else {
                    viewModel.clearStatusMessage()
                }
            },
        )
    }
}

@Composable
private fun DashboardScreen(
    state: AccountingUiState,
    padding: PaddingValues,
    onOpenDialog: (EntryDialog) -> Unit,
    onUndoRecurringGeneration: () -> Unit,
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
                    value = state.dashboard.monthlyConsumption.formatCurrency(),
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = "当前净资产",
                    value = state.dashboard.netWorth.formatCurrency(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(
                    title = "固定支出",
                    value = state.dashboard.monthlyFixedExpense.formatCurrency(),
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = "投资投入",
                    value = state.dashboard.monthlyInvestmentInput.formatCurrency(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            SummaryCard(
                title = "投资浮盈浮亏",
                value = state.dashboard.investmentGainLoss.formatCurrency(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("快捷操作", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onOpenDialog(EntryDialog.EXPENSE) },
                        enabled = state.accounts.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("记支出") }
                    Button(
                        onClick = { onOpenDialog(EntryDialog.INCOME) },
                        enabled = state.accounts.any { it.type != AccountType.CREDIT_CARD },
                        modifier = Modifier.weight(1f),
                    ) { Text("记收入") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onOpenDialog(EntryDialog.INVESTMENT_BUY) },
                        enabled = state.accounts.any { it.type != AccountType.CREDIT_CARD } && state.investments.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("投资买入") }
                    Button(
                        onClick = { onOpenDialog(EntryDialog.VALUATION) },
                        enabled = state.investments.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("更新市值") }
                }
                Button(
                    onClick = { onOpenDialog(EntryDialog.CREDIT_CARD_REPAYMENT) },
                    enabled = state.accounts.any { it.type != AccountType.CREDIT_CARD } &&
                        state.accounts.any { it.type == AccountType.CREDIT_CARD },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("信用卡还款") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onOpenDialog(EntryDialog.REFUND) },
                        enabled = state.accounts.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("退款") }
                    Button(
                        onClick = { onOpenDialog(EntryDialog.TRANSFER) },
                        enabled = state.accounts.count { it.type != AccountType.CREDIT_CARD } >= 2,
                        modifier = Modifier.weight(1f),
                    ) { Text("转账") }
                }
            }
        }
        item {
            RecurringGenerationCard(
                count = state.recurringGenerationCount,
                onUndo = onUndoRecurringGeneration,
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
private fun RecurringGenerationCard(count: Int, onUndo: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("自动周期账单", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (count > 0) {
                Text("本次运行已自动补记 $count 条会员订阅、自动续费或定投流水。", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onUndo, modifier = Modifier.fillMaxWidth()) {
                    Text("撤销本次补记")
                }
            } else {
                Text("打开 App 时会自动补记会员订阅、自动续费和定投。当前没有待补记项目。", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TransactionListScreen(
    transactions: List<Transaction>,
    categories: List<Category>,
    accounts: List<Account>,
    investments: List<InvestmentAsset>,
    padding: PaddingValues,
) {
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedType by remember { mutableStateOf<TransactionType?>(null) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    val filteredTransactions = TransactionFilter.filter(
        transactions = transactions,
        month = selectedMonth,
        type = selectedType,
        categoryId = selectedCategory?.id,
    )
    val categoryNames = categories.associate { it.id to it.historyLabel() }
    val accountNames = accounts.associate { it.id to it.name }
    val investmentNames = investments.associate { it.id to it.name }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("transaction-list")
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("流水", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { selectedMonth = selectedMonth.minusMonths(1) },
                            modifier = Modifier.weight(1f),
                        ) { Text("上月") }
                        Button(
                            onClick = { selectedMonth = YearMonth.now() },
                            modifier = Modifier.weight(1f),
                        ) { Text("${selectedMonth.year}-${selectedMonth.monthValue.toString().padStart(2, '0')}") }
                        Button(
                            onClick = { selectedMonth = selectedMonth.plusMonths(1) },
                            modifier = Modifier.weight(1f),
                        ) { Text("下月") }
                    }
                    TransactionTypeFilterSelector(selected = selectedType, onSelected = { selectedType = it })
                    CategoryFilterSelector(
                        categories = categories,
                        selected = selectedCategory,
                        onSelected = { selectedCategory = it },
                    )
                }
            }
        }
        if (filteredTransactions.isEmpty()) {
            item { InfoCard(title = "暂无流水", body = "当前筛选条件下没有流水。") }
        } else {
            filteredTransactions.forEach { transaction ->
                item {
                    val categoryText = transaction.categoryId
                        ?.let { categoryNames[it] ?: it }
                        ?.let { " / $it" }
                        .orEmpty()
                    val autoText = if (transaction.autoGenerated) " / 自动补记" else ""
                    val accountSummary = transactionAccountSummary(
                        transaction = transaction,
                        accountNames = accountNames,
                        investmentNames = investmentNames,
                    )
                    InfoCard(
                        title = "${transaction.type.label()} ${transaction.amount.formatCurrency()}",
                        body = "$accountSummary\n${transaction.date}$categoryText$autoText " +
                            transaction.note.ifBlank { "无备注" },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountListScreen(
    accounts: List<Account>,
    transactions: List<Transaction>,
    padding: PaddingValues,
    onCreateAccount: () -> Unit,
    onArchiveAccount: (Account) -> Unit,
) {
    var selectedAccount by remember(accounts) { mutableStateOf<Account?>(null) }
    val accountTransactions = selectedAccount?.let { account ->
        TransactionFilter.filter(transactions, accountId = account.id)
    }.orEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("账户", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Button(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth()) {
                Text("新增账户")
            }
        }
        accounts.forEach { account ->
            item {
                AccountCard(
                    account = account,
                    selected = selectedAccount?.id == account.id,
                    onShowTransactions = {
                        selectedAccount = if (selectedAccount?.id == account.id) null else account
                    },
                    onArchiveAccount = { onArchiveAccount(account) },
                )
            }
        }
        selectedAccount?.let { account ->
            item {
                Text(
                    "${account.name} 流水明细",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (accountTransactions.isEmpty()) {
                item { InfoCard(title = "暂无账户流水", body = "这个账户还没有相关流水。") }
            } else {
                accountTransactions.forEach { transaction ->
                    item {
                        AccountTransactionCard(
                            transaction = transaction,
                            selectedAccountId = account.id,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: Account,
    selected: Boolean,
    onShowTransactions: () -> Unit,
    onArchiveAccount: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (account.type == AccountType.CREDIT_CARD) {
                    "未还负债 ${account.balance.formatCurrency()}"
                } else {
                    "余额 ${account.balance.formatCurrency()}"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (account.type == AccountType.CREDIT_CARD) {
                Text(
                    "信用额度 ${account.creditLimit?.formatCurrency() ?: "未设置"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "账单日 ${account.billingDay?.let { "$it 日" } ?: "未设置"} / " +
                        "还款日 ${account.repaymentDay?.let { "$it 日" } ?: "未设置"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(onClick = onShowTransactions, modifier = Modifier.fillMaxWidth()) {
                Text(if (selected) "收起账户流水" else "查看账户流水")
            }
            Button(onClick = onArchiveAccount, modifier = Modifier.fillMaxWidth()) {
                Text("归档账户")
            }
        }
    }
}

@Composable
private fun AccountTransactionCard(transaction: Transaction, selectedAccountId: String) {
    val direction = when {
        transaction.accountId == selectedAccountId && transaction.targetAccountId == selectedAccountId -> "本账户"
        transaction.targetAccountId == selectedAccountId -> "转入"
        else -> "转出"
    }
    InfoCard(
        title = "$direction ${transaction.type.label()} ${transaction.amount.formatCurrency()}",
        body = "${transaction.date} ${transaction.note.ifBlank { "无备注" }}",
    )
}

@Composable
private fun InvestmentListScreen(
    investments: List<InvestmentAsset>,
    valuations: List<ValuationSnapshot>,
    padding: PaddingValues,
    onCreateInvestment: () -> Unit,
    onUpdateValuation: () -> Unit,
    onArchiveInvestment: (InvestmentAsset) -> Unit,
) {
    var expandedInvestmentIds by remember(investments) { mutableStateOf(emptySet<String>()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("投资", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Button(onClick = onCreateInvestment, modifier = Modifier.fillMaxWidth()) {
                Text("新增投资资产")
            }
        }
        item {
            Button(onClick = onUpdateValuation, modifier = Modifier.fillMaxWidth()) {
                Text("更新市值")
            }
        }
        investments.forEach { investment ->
            val investmentValuations = valuations.filter { it.investmentAssetId == investment.id }
            val historyExpanded = investment.id in expandedInvestmentIds
            item(key = investment.id) {
                val gainLoss = LedgerCalculator.investmentGainLoss(investment)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(investment.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "本金 ${investment.principal.formatCurrency()} / 市值 ${investment.currentValue.formatCurrency()} / 浮盈浮亏 ${gainLoss.formatCurrency()}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "最近市值 ${investment.lastValuationDate?.toString() ?: "未更新"}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = {
                                expandedInvestmentIds = if (historyExpanded) {
                                    expandedInvestmentIds - investment.id
                                } else {
                                    expandedInvestmentIds + investment.id
                                }
                            },
                            enabled = investmentValuations.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("valuation-history-toggle-${investment.id}"),
                        ) {
                            Text(
                                when {
                                    investmentValuations.isEmpty() -> "暂无历史市值"
                                    historyExpanded -> "收起历史市值 (${investmentValuations.size})"
                                    else -> "查看历史市值 (${investmentValuations.size})"
                                }
                            )
                        }
                        Button(onClick = { onArchiveInvestment(investment) }, modifier = Modifier.fillMaxWidth()) {
                            Text("归档投资资产")
                        }
                    }
                }
            }
            if (historyExpanded) {
                investmentValuations.forEach { snapshot ->
                    item(key = snapshot.id) {
                        InfoCard(
                            title = "${snapshot.date} 市值 ${snapshot.value.formatCurrency()}",
                            body = snapshot.note.ifBlank { investment.name },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    padding: PaddingValues,
    categories: List<Category>,
    recurringRules: List<RecurringRule>,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onCreateCategory: () -> Unit,
    onArchiveCategory: (Category) -> Unit,
    onCreateRecurringRule: () -> Unit,
    onSkipNextOccurrence: (RecurringRule) -> Unit,
    onSetRecurringRuleEnabled: (RecurringRule, Boolean) -> Unit,
    onResetLocalData: () -> Unit,
) {
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
        item {
            CategoryManagementCard(
                categories = categories,
                onCreateCategory = onCreateCategory,
                onArchiveCategory = onArchiveCategory,
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("周期规则", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("管理会员订阅、自动续费和定投规则。", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onCreateRecurringRule, modifier = Modifier.fillMaxWidth()) {
                        Text("新增周期规则")
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("导入导出", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("手动导出或导入本地 JSON 备份。", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                            Text("导出 JSON")
                        }
                        Button(onClick = onImport, modifier = Modifier.weight(1f)) {
                            Text("导入 JSON")
                        }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("本地数据维护", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("清空所有本地记录并恢复默认账户、分类和投资资产。", style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = onResetLocalData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset-local-data"),
                    ) {
                        Text("清空并恢复默认数据")
                    }
                }
            }
        }
        item {
            RecurringRulesSection(
                recurringRules = recurringRules,
                onSkipNextOccurrence = onSkipNextOccurrence,
                onSetRecurringRuleEnabled = onSetRecurringRuleEnabled,
            )
        }
    }
}

@Composable
private fun CategoryManagementCard(
    categories: List<Category>,
    onCreateCategory: () -> Unit,
    onArchiveCategory: (Category) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("分类管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Button(onClick = onCreateCategory, modifier = Modifier.fillMaxWidth()) {
                Text("新增分类")
            }
            categoryTypes.forEach { type ->
                val typedCategories = categories.filter { it.type == type }
                if (typedCategories.isNotEmpty()) {
                    Text(type.categoryTypeLabel(), style = MaterialTheme.typography.labelLarge)
                    typedCategories.forEach { category ->
                        CategoryRow(
                            category = category,
                            onArchive = { onArchiveCategory(category) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: Category, onArchive: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(category.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (category.isFixedExpense) "固定支出" else category.type.categoryTypeLabel(),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (category.id !in requiredSystemCategoryIds) {
            Button(
                onClick = onArchive,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("archive-category-${category.name}"),
            ) {
                Text("归档分类")
            }
        }
    }
}

@Composable
private fun RecurringRulesSection(
    recurringRules: List<RecurringRule>,
    onSkipNextOccurrence: (RecurringRule) -> Unit,
    onSetRecurringRuleEnabled: (RecurringRule, Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("周期规则列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (recurringRules.isEmpty()) {
                Text("暂无周期规则。", style = MaterialTheme.typography.bodyMedium)
            } else {
                recurringRules.forEach { rule ->
                    RecurringRuleRow(
                        rule = rule,
                        onSkipNextOccurrence = onSkipNextOccurrence,
                        onSetEnabled = onSetRecurringRuleEnabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecurringRuleRow(
    rule: RecurringRule,
    onSkipNextOccurrence: (RecurringRule) -> Unit,
    onSetEnabled: (RecurringRule, Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(rule.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${rule.transactionType.label()} / ${rule.frequency.label()} / 下期 ${rule.nextOccurrenceDate}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "起止 ${rule.startDate} - ${rule.endDate?.toString() ?: "长期"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (rule.enabled) "状态 启用" else "状态 停用",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { onSkipNextOccurrence(rule) },
                enabled = rule.enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("跳过下一期")
            }
            Button(
                onClick = { onSetEnabled(rule, !rule.enabled) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (rule.enabled) "停用规则" else "启用规则")
            }
        }
    }
}

@Composable
private fun MoneyEntryDialog(
    title: String,
    accounts: List<Account>,
    categories: List<Category>,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (Money, Account, Category?, LocalDate, String) -> Unit,
) {
    if (accounts.isEmpty()) {
        SimpleMessageDialog(title = title, message = "暂无可用账户", onDismiss = onDismiss)
        return
    }
    var amountText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(LocalDate.now().toString()) }
    var note by remember { mutableStateOf("") }
    var selectedAccount by remember(accounts) { mutableStateOf(accounts.first()) }
    var selectedCategory by remember(categories) { mutableStateOf(categories.firstOrNull()) }
    val amount = amountText.toMoneyOrNull()
    val date = dateText.toLocalDateOrNull()

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
                    modifier = Modifier.testTag("money-entry-amount"),
                )
                AccountSelector(
                    accounts = accounts,
                    selected = selectedAccount,
                    onSelected = { selectedAccount = it },
                    testTag = "money-entry-account",
                )
                if (categories.isNotEmpty() && selectedCategory != null) {
                    CategorySelector(
                        categories = categories,
                        selected = requireNotNull(selectedCategory),
                        onSelected = { selectedCategory = it },
                        testTag = "money-entry-category",
                    )
                }
                DateInput(value = dateText, onValueChange = { dateText = it })
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    singleLine = true,
                    modifier = Modifier.testTag("money-entry-note"),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && date != null,
                onClick = { onConfirm(requireNotNull(amount), selectedAccount, selectedCategory, requireNotNull(date), note) },
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun InvestmentAssetDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, InvestmentType, Money, Money) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(InvestmentType.FUND) }
    var principalText by remember { mutableStateOf("") }
    var currentValueText by remember { mutableStateOf("") }
    val principal = principalText.toOptionalMoneyOrNull()
    val currentValue = currentValueText.toOptionalMoneyOrNull()
    val canSave = name.isNotBlank() && principal != null && currentValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增投资资产") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("资产名称") })
                InvestmentTypeSelector(selected = type, onSelected = { type = it })
                OutlinedTextField(value = principalText, onValueChange = { principalText = it }, label = { Text("累计本金") })
                OutlinedTextField(value = currentValueText, onValueChange = { currentValueText = it }, label = { Text("当前市值") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onConfirm(name, type, requireNotNull(principal), requireNotNull(currentValue)) },
            ) { Text("保存资产") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AccountDialog(
    onDismiss: () -> Unit,
    onConfirmAsset: (String, AccountType, Money) -> Unit,
    onConfirmCreditCard: (String, Money, Money?, Int?, Int?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.BANK_CARD) }
    var balanceText by remember { mutableStateOf("") }
    var creditLimitText by remember { mutableStateOf("") }
    var billingDayText by remember { mutableStateOf("") }
    var repaymentDayText by remember { mutableStateOf("") }
    val balance = balanceText.toOptionalMoneyOrNull()
    val creditLimit = creditLimitText.toOptionalMoneyOrNull()
    val billingDay = billingDayText.toOptionalDayOrNull()
    val repaymentDay = repaymentDayText.toOptionalDayOrNull()
    val canSave = name.isNotBlank() &&
        balance != null &&
        (
            type != AccountType.CREDIT_CARD ||
                (
                    creditLimit != null &&
                        (billingDayText.isBlank() || billingDay != null) &&
                        (repaymentDayText.isBlank() || repaymentDay != null)
                    )
            )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增账户") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("账户名称") })
                AccountTypeSelector(selected = type, onSelected = { type = it })
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text(if (type == AccountType.CREDIT_CARD) "当前未还" else "初始余额") },
                )
                if (type == AccountType.CREDIT_CARD) {
                    OutlinedTextField(
                        value = creditLimitText,
                        onValueChange = { creditLimitText = it },
                        label = { Text("信用额度") },
                    )
                    OutlinedTextField(
                        value = billingDayText,
                        onValueChange = { billingDayText = it },
                        label = { Text("账单日") },
                    )
                    OutlinedTextField(
                        value = repaymentDayText,
                        onValueChange = { repaymentDayText = it },
                        label = { Text("还款日") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    if (type == AccountType.CREDIT_CARD) {
                        onConfirmCreditCard(
                            name,
                            requireNotNull(balance),
                            requireNotNull(creditLimit).takeUnless { creditLimitText.isBlank() },
                            billingDay,
                            repaymentDay,
                        )
                    } else {
                        onConfirmAsset(name, type, requireNotNull(balance))
                    }
                },
            ) { Text("保存账户") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun TransferDialog(
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (Money, Account, Account, Category?, LocalDate, String) -> Unit,
) {
    if (accounts.size < 2) {
        SimpleMessageDialog(title = "账户转账", message = "需要至少两个资产账户", onDismiss = onDismiss)
        return
    }
    var amountText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(LocalDate.now().toString()) }
    var note by remember { mutableStateOf("") }
    var selectedSource by remember(accounts) { mutableStateOf(accounts.first()) }
    var selectedTarget by remember(accounts) { mutableStateOf(accounts.first { it.id != selectedSource.id }) }
    var selectedCategory by remember(categories) { mutableStateOf(categories.firstOrNull()) }
    val amount = amountText.toMoneyOrNull()
    val date = dateText.toLocalDateOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("账户转账") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("转账金额") })
                AccountSelector(
                    accounts = accounts,
                    selected = selectedSource,
                    onSelected = { account ->
                        selectedSource = account
                        if (selectedTarget.id == account.id) {
                            selectedTarget = accounts.first { it.id != account.id }
                        }
                    },
                )
                AccountSelector(
                    accounts = accounts.filter { it.id != selectedSource.id },
                    selected = selectedTarget,
                    onSelected = { selectedTarget = it },
                )
                if (selectedCategory != null) {
                    CategorySelector(
                        categories = categories,
                        selected = requireNotNull(selectedCategory),
                        onSelected = { selectedCategory = it },
                    )
                }
                DateInput(value = dateText, onValueChange = { dateText = it })
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && date != null && selectedSource.id != selectedTarget.id,
                onClick = {
                    onConfirm(
                        requireNotNull(amount),
                        selectedSource,
                        selectedTarget,
                        selectedCategory,
                        requireNotNull(date),
                        note,
                    )
                },
            ) { Text("保存转账") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RecurringRuleDialog(
    accounts: List<Account>,
    investments: List<InvestmentAsset>,
    onDismiss: () -> Unit,
    onConfirmSubscription: (String, Money, Account, RecurringFrequency, LocalDate, LocalDate?) -> Unit,
    onConfirmInvestmentBuy: (String, Money, Account, InvestmentAsset, RecurringFrequency, LocalDate, LocalDate?) -> Unit,
) {
    if (accounts.isEmpty()) {
        SimpleMessageDialog(title = "新增周期规则", message = "需要至少一个资产账户", onDismiss = onDismiss)
        return
    }

    var isInvestmentRule by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var startDateText by remember { mutableStateOf(LocalDate.now().toString()) }
    var endDateText by remember { mutableStateOf("") }
    var selectedAccount by remember(accounts) { mutableStateOf(accounts.first()) }
    var selectedInvestment by remember(investments) { mutableStateOf(investments.firstOrNull()) }
    var frequency by remember { mutableStateOf(RecurringFrequency.MONTHLY) }
    val amount = amountText.toMoneyOrNull()
    val startDate = startDateText.toLocalDateOrNull()
    val endDate = if (endDateText.isBlank()) null else endDateText.toLocalDateOrNull()
    val endDateIsValid = endDateText.isBlank() || endDate != null
    val canSave = name.isNotBlank() &&
        amount != null &&
        startDate != null &&
        endDateIsValid &&
        (endDate == null || !endDate.isBefore(startDate)) &&
        (!isInvestmentRule || selectedInvestment != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增周期规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { isInvestmentRule = false },
                        modifier = Modifier.weight(1f),
                    ) { Text("订阅") }
                    Button(
                        onClick = { isInvestmentRule = true },
                        enabled = investments.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("定投") }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    modifier = Modifier.testTag("recurring-name"),
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("金额") },
                    modifier = Modifier.testTag("recurring-amount"),
                )
                AccountSelector(accounts = accounts, selected = selectedAccount, onSelected = { selectedAccount = it })
                if (isInvestmentRule && selectedInvestment != null) {
                    InvestmentSelector(
                        investments = investments,
                        selected = requireNotNull(selectedInvestment),
                        onSelected = { selectedInvestment = it },
                    )
                }
                FrequencySelector(selected = frequency, onSelected = { frequency = it })
                DateInput(value = startDateText, onValueChange = { startDateText = it }, label = "开始日期")
                DateInput(value = endDateText, onValueChange = { endDateText = it }, label = "结束日期（可选）")
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val value = requireNotNull(amount)
                    if (isInvestmentRule) {
                        onConfirmInvestmentBuy(
                            name,
                            value,
                            selectedAccount,
                            requireNotNull(selectedInvestment),
                            frequency,
                            requireNotNull(startDate),
                            endDate,
                        )
                    } else {
                        onConfirmSubscription(name, value, selectedAccount, frequency, requireNotNull(startDate), endDate)
                    }
                },
            ) { Text("保存规则") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CreditCardRepaymentDialog(
    sourceAccounts: List<Account>,
    creditCards: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (Money, Account, Account, LocalDate, String) -> Unit,
) {
    if (sourceAccounts.isEmpty() || creditCards.isEmpty()) {
        SimpleMessageDialog(title = "信用卡还款", message = "需要至少一个资产账户和一个信用卡账户", onDismiss = onDismiss)
        return
    }
    var amountText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(LocalDate.now().toString()) }
    var note by remember { mutableStateOf("") }
    var selectedSource by remember(sourceAccounts) { mutableStateOf(sourceAccounts.first()) }
    var selectedCard by remember(creditCards) { mutableStateOf(creditCards.first()) }
    val amount = amountText.toMoneyOrNull()
    val date = dateText.toLocalDateOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("信用卡还款") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("还款金额") },
                    modifier = Modifier.testTag("repayment-amount"),
                )
                AccountSelector(accounts = sourceAccounts, selected = selectedSource, onSelected = { selectedSource = it })
                AccountSelector(accounts = creditCards, selected = selectedCard, onSelected = { selectedCard = it })
                DateInput(value = dateText, onValueChange = { dateText = it })
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    modifier = Modifier.testTag("repayment-note"),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && date != null,
                onClick = { onConfirm(requireNotNull(amount), selectedSource, selectedCard, requireNotNull(date), note) },
            ) { Text("保存还款") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun InvestmentBuyDialog(
    accounts: List<Account>,
    investments: List<InvestmentAsset>,
    onDismiss: () -> Unit,
    onConfirm: (Money, Account, InvestmentAsset, LocalDate, String) -> Unit,
) {
    if (accounts.isEmpty() || investments.isEmpty()) {
        SimpleMessageDialog(title = "投资买入", message = "需要至少一个资产账户和一个投资资产", onDismiss = onDismiss)
        return
    }
    var amountText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(LocalDate.now().toString()) }
    var note by remember { mutableStateOf("") }
    var selectedAccount by remember(accounts) { mutableStateOf(accounts.first()) }
    var selectedInvestment by remember(investments) { mutableStateOf(investments.first()) }
    val amount = amountText.toMoneyOrNull()
    val date = dateText.toLocalDateOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("投资买入") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("金额") },
                    modifier = Modifier.testTag("investment-buy-amount"),
                )
                AccountSelector(accounts = accounts, selected = selectedAccount, onSelected = { selectedAccount = it })
                InvestmentSelector(
                    investments = investments,
                    selected = selectedInvestment,
                    onSelected = { selectedInvestment = it },
                )
                DateInput(value = dateText, onValueChange = { dateText = it })
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    modifier = Modifier.testTag("investment-buy-note"),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && date != null,
                onClick = { onConfirm(requireNotNull(amount), selectedAccount, selectedInvestment, requireNotNull(date), note) },
            ) { Text("保存买入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ValuationDialog(
    investments: List<InvestmentAsset>,
    onDismiss: () -> Unit,
    onConfirm: (InvestmentAsset, Money, LocalDate) -> Unit,
) {
    if (investments.isEmpty()) {
        SimpleMessageDialog(title = "更新投资市值", message = "暂无投资资产", onDismiss = onDismiss)
        return
    }
    var amountText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(LocalDate.now().toString()) }
    var selectedInvestment by remember(investments) { mutableStateOf(investments.first()) }
    val amount = amountText.toNonNegativeMoneyOrNull()
    val date = dateText.toLocalDateOrNull()

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
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("当前市值") },
                    modifier = Modifier.testTag("valuation-value"),
                )
                DateInput(value = dateText, onValueChange = { dateText = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && date != null,
                onClick = { onConfirm(selectedInvestment, requireNotNull(amount), requireNotNull(date)) },
            ) { Text("更新") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("expense") }
    var isFixedExpense by remember { mutableStateOf(false) }
    val canSave = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增分类") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分类名称") },
                    modifier = Modifier.testTag("category-name"),
                )
                CategoryTypeSelector(selected = type, onSelected = { selectedType ->
                    type = selectedType
                    if (selectedType != "expense") {
                        isFixedExpense = false
                    }
                })
                if (type == "expense") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("固定支出")
                        Switch(
                            checked = isFixedExpense,
                            onCheckedChange = { isFixedExpense = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onConfirm(name.trim(), type, isFixedExpense) },
            ) { Text("保存分类") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DateInput(value: String, onValueChange: (String) -> Unit, label: String = "日期") {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("yyyy-MM-dd") },
        singleLine = true,
    )
}

@Composable
private fun SimpleMessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencySelector(
    selected: RecurringFrequency,
    onSelected: (RecurringFrequency) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text("频率") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RecurringFrequency.entries.forEach { frequency ->
                DropdownMenuItem(
                    text = { Text(frequency.label()) },
                    onClick = {
                        onSelected(frequency)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvestmentTypeSelector(
    selected: InvestmentType,
    onSelected: (InvestmentType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text("投资类型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            InvestmentType.entries.forEach { investmentType ->
                DropdownMenuItem(
                    text = { Text(investmentType.label()) },
                    onClick = {
                        onSelected(investmentType)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionTypeFilterSelector(
    selected: TransactionType?,
    onSelected: (TransactionType?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.label() ?: "全部类型",
            onValueChange = {},
            readOnly = true,
            label = { Text("类型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("全部类型") },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            TransactionType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label()) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterSelector(
    categories: List<Category>,
    selected: Category?,
    onSelected: (Category?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.historyLabel() ?: "全部分类",
            onValueChange = {},
            readOnly = true,
            label = { Text("分类") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("全部分类") },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.historyLabel()) },
                    onClick = {
                        onSelected(category)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryTypeSelector(
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.categoryTypeLabel(),
            onValueChange = {},
            readOnly = true,
            label = { Text("分类类型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categoryTypes.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.categoryTypeLabel()) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountTypeSelector(
    selected: AccountType,
    onSelected: (AccountType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text("账户类型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AccountType.entries.forEach { accountType ->
                DropdownMenuItem(
                    text = { Text(accountType.label()) },
                    onClick = {
                        onSelected(accountType)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelector(
    categories: List<Category>,
    selected: Category,
    onSelected: (Category) -> Unit,
    testTag: String = "category-selector",
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("分类") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .testTag(testTag),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onSelected(category)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSelector(
    accounts: List<Account>,
    selected: Account,
    onSelected: (Account) -> Unit,
    testTag: String = "account-selector",
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("账户") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .testTag(testTag)
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
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
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
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

internal fun transactionAccountSummary(
    transaction: Transaction,
    accountNames: Map<String, String>,
    investmentNames: Map<String, String>,
): String {
    fun accountName(id: String?): String = id?.let { accountNames[it] ?: it } ?: "未指定账户"
    fun investmentName(id: String?): String = id?.let { investmentNames[it] ?: it } ?: "未指定投资资产"

    val source = accountName(transaction.accountId)
    return when (transaction.type) {
        TransactionType.EXPENSE -> "支出账户 $source"
        TransactionType.INCOME -> "收入账户 $source"
        TransactionType.REFUND -> "退款账户 $source"
        TransactionType.TRANSFER -> "转账 $source → ${accountName(transaction.targetAccountId)}"
        TransactionType.CREDIT_CARD_REPAYMENT -> "还款 $source → ${accountName(transaction.targetAccountId)}"
        TransactionType.INVESTMENT_BUY -> "投资 $source → ${investmentName(transaction.investmentAssetId)}"
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

private fun AccountType.label(): String =
    when (this) {
        AccountType.CASH -> "现金"
        AccountType.BANK_CARD -> "银行卡"
        AccountType.ALIPAY -> "支付宝"
        AccountType.WECHAT -> "微信"
        AccountType.CREDIT_CARD -> "信用卡"
    }

private fun InvestmentType.label(): String =
    when (this) {
        InvestmentType.FUND -> "基金"
        InvestmentType.GOLD -> "黄金"
        InvestmentType.WEALTH_MANAGEMENT -> "理财"
    }

private fun RecurringFrequency.label(): String =
    when (this) {
        RecurringFrequency.DAILY -> "每天"
        RecurringFrequency.WEEKLY -> "每周"
        RecurringFrequency.MONTHLY -> "每月"
    }

private fun String.categoryTypeLabel(): String =
    when (this) {
        "expense" -> "支出"
        "income" -> "收入"
        "transfer" -> "转账"
        "investment" -> "投资"
        else -> this
    }

private fun Category.historyLabel(): String =
    if (isArchived) "$name（已归档）" else name

private fun String.toOptionalMoneyOrNull(): Money? =
    if (isBlank()) {
        Money.ZERO
    } else {
        toMoneyOrNull()
    }

private fun String.toOptionalDayOrNull(): Int? {
    if (isBlank()) return null
    val day = toIntOrNull() ?: return null
    return day.takeIf { it in 1..31 }
}

private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(trim()) }.getOrNull()
