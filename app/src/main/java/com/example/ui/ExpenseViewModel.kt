package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.ai.GeminiExpenseParser
import com.example.data.AppDatabase
import com.example.data.ExpenseRepository
import com.example.media.ReceiptMediaLoader
import com.example.model.CategoryShare
import com.example.model.DraftExpense
import com.example.model.ExpenseCategory
import com.example.model.ExpenseFormatters
import com.example.model.ExpenseItem
import com.example.model.TransactionType
import com.example.model.YearMonthKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAG = "ExpenseViewModel"

sealed interface AnalysisUiState {
    data object Idle : AnalysisUiState
    data class Loading(val statusMessage: String) : AnalysisUiState
    data class Error(val message: String) : AnalysisUiState
}

sealed interface SnackbarMessage {
    data class Info(val message: String) : SnackbarMessage
    data class DeletedWithUndo(val expense: ExpenseItem, val message: String, val undoLabel: String) : SnackbarMessage
}

data class GastoRapidoUiState(
    val selectedMonth: YearMonthKey = YearMonthKey.current(),
    val selectedCategory: ExpenseCategory? = null,
    val allMonthExpenses: List<ExpenseItem> = emptyList(),
    val filteredExpenses: List<ExpenseItem> = emptyList(),
    val monthlyBalance: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val categoryShares: List<CategoryShare> = emptyList(),
    val inputText: String = "",
    val analysisState: AnalysisUiState = AnalysisUiState.Idle,
    val pendingDrafts: List<DraftExpense> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExpenseRepository(
        AppDatabase.getInstance(application).expenseDao()
    )
    private val geminiParser = GeminiExpenseParser()

    private val selectedMonthFlow = MutableStateFlow(YearMonthKey.current())
    private val selectedCategoryFlow = MutableStateFlow<ExpenseCategory?>(null)
    private val inputTextFlow = MutableStateFlow("")
    private val analysisStateFlow = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    private val pendingDraftsFlow = MutableStateFlow<List<DraftExpense>>(emptyList())

    private val _snackbarEvents = MutableSharedFlow<SnackbarMessage>(extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<SnackbarMessage> = _snackbarEvents.asSharedFlow()

    private val monthExpensesFlow = selectedMonthFlow.flatMapLatest { month ->
        repository.observeExpensesForMonth(month.storageKey)
    }

    private data class CoreSelectionState(
        val selectedMonth: YearMonthKey,
        val selectedCategory: ExpenseCategory?,
        val monthExpenses: List<ExpenseItem>
    )

    private val selectionStateFlow = combine(
        selectedMonthFlow,
        selectedCategoryFlow,
        monthExpensesFlow
    ) { month, category, expenses ->
        CoreSelectionState(month, category, expenses)
    }

    val uiState: StateFlow<GastoRapidoUiState> = combine(
        selectionStateFlow,
        inputTextFlow,
        analysisStateFlow,
        pendingDraftsFlow
    ) { selection, inputText, analysisState, drafts ->
        val monthEntries = selection.monthExpenses
        val monthlyIncome = monthEntries
            .filter { it.type == TransactionType.GANHO }
            .sumOf { it.amount }
        val monthlyExpenses = monthEntries
            .filter { it.type == TransactionType.GASTO }
            .sumOf { it.amount }
        val monthlyBalance = monthlyIncome - monthlyExpenses
        val filtered = if (selection.selectedCategory == null) {
            monthEntries
        } else {
            monthEntries.filter { it.category == selection.selectedCategory }
        }
        val expensesOnly = monthEntries.filter { it.type == TransactionType.GASTO }
        val sharesSource = if (expensesOnly.isNotEmpty()) expensesOnly else monthEntries
        val shares = computeCategoryShares(sharesSource, sharesSource.sumOf { it.amount })

        GastoRapidoUiState(
            selectedMonth = selection.selectedMonth,
            selectedCategory = selection.selectedCategory,
            allMonthExpenses = monthEntries,
            filteredExpenses = filtered,
            monthlyBalance = monthlyBalance,
            monthlyIncome = monthlyIncome,
            monthlyExpenses = monthlyExpenses,
            categoryShares = shares,
            inputText = inputText,
            analysisState = analysisState,
            pendingDrafts = drafts
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = GastoRapidoUiState()
    )

    fun previousMonth() {
        selectedMonthFlow.value = selectedMonthFlow.value.previous()
    }

    fun nextMonth() {
        selectedMonthFlow.value = selectedMonthFlow.value.next()
    }

    fun selectCategory(category: ExpenseCategory?) {
        selectedCategoryFlow.value = if (selectedCategoryFlow.value == category) null else category
    }

    fun clearCategoryFilter() {
        selectedCategoryFlow.value = null
    }

    fun onInputTextChanged(text: String) {
        inputTextFlow.value = text
    }

    fun applyExamplePrompt(example: String) {
        inputTextFlow.value = example
    }

    fun dismissError() {
        if (analysisStateFlow.value is AnalysisUiState.Error) {
            analysisStateFlow.value = AnalysisUiState.Idle
        }
    }

    fun showTransientMessage(message: String) {
        _snackbarEvents.tryEmit(SnackbarMessage.Info(message))
    }

    fun submitTextExpense() {
        val rawText = inputTextFlow.value.trim()
        if (rawText.isEmpty() || analysisStateFlow.value is AnalysisUiState.Loading) return

        val app = getApplication<Application>()
        viewModelScope.launch {
            analysisStateFlow.value = AnalysisUiState.Loading(
                app.getString(R.string.analyzing_text)
            )
            try {
                val today = ExpenseFormatters.todayFormatted()
                val extracted = geminiParser.parseFromText(rawText, today)
                if (extracted.isEmpty()) {
                    analysisStateFlow.value = AnalysisUiState.Error(
                        app.getString(R.string.error_empty_extraction)
                    )
                } else {
                    inputTextFlow.value = ""
                    analysisStateFlow.value = AnalysisUiState.Idle
                    pendingDraftsFlow.value = extracted
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Text parsing timed out", e)
                analysisStateFlow.value = AnalysisUiState.Error(app.getString(R.string.error_timeout))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Text parsing failed", e)
                val detail = e.localizedMessage ?: "Erro inesperado"
                analysisStateFlow.value = AnalysisUiState.Error(
                    app.getString(R.string.error_generic, detail)
                )
            }
        }
    }

    fun analyzeMediaUri(
        uri: Uri,
        explicitMimeType: String? = null,
        isFromShareIntent: Boolean = false
    ) {
        if (analysisStateFlow.value is AnalysisUiState.Loading) return

        val app = getApplication<Application>()
        val isLikelyPdf = ReceiptMediaLoader.isLikelyPdfUri(app, uri, explicitMimeType)
        val loadingLabel = when {
            isFromShareIntent -> app.getString(R.string.analyzing_pix_share)
            isLikelyPdf -> app.getString(R.string.analyzing_pdf)
            else -> app.getString(R.string.analyzing_photo)
        }

        viewModelScope.launch {
            analysisStateFlow.value = AnalysisUiState.Loading(loadingLabel)
            try {
                val preparedMedia = ReceiptMediaLoader.loadReceiptMedia(app, uri, explicitMimeType)
                val sourceHint = when {
                    isFromShareIntent && preparedMedia.isPdf -> "comprovante Pix em PDF compartilhado"
                    isFromShareIntent -> "comprovante Pix compartilhado"
                    preparedMedia.isPdf -> "comprovante PDF"
                    else -> "cupom ou comprovante em imagem"
                }
                val today = ExpenseFormatters.todayFormatted()
                val extracted = geminiParser.parseFromReceiptJpeg(
                    jpegBytes = preparedMedia.jpegBytes,
                    todayDate = today,
                    sourceHint = sourceHint
                )
                if (extracted.isEmpty()) {
                    analysisStateFlow.value = AnalysisUiState.Error(
                        app.getString(R.string.error_empty_extraction)
                    )
                } else {
                    analysisStateFlow.value = AnalysisUiState.Idle
                    pendingDraftsFlow.value = extracted
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Media parsing timed out", e)
                analysisStateFlow.value = AnalysisUiState.Error(app.getString(R.string.error_timeout))
            } catch (e: CancellationException) {
                throw e
            } catch (e: com.example.media.ReceiptMediaLoadException) {
                Log.e(TAG, "Media load failed", e)
                analysisStateFlow.value = AnalysisUiState.Error(
                    e.message ?: app.getString(R.string.error_media_load)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Media parsing failed", e)
                val detail = e.localizedMessage ?: "Erro inesperado"
                analysisStateFlow.value = AnalysisUiState.Error(
                    app.getString(R.string.error_generic, detail)
                )
            }
        }
    }

    fun updateDraftDescription(tempId: String, description: String) {
        pendingDraftsFlow.value = pendingDraftsFlow.value.map { draft ->
            if (draft.tempId == tempId) draft.copy(description = description) else draft
        }
    }

    fun updateDraftAmount(tempId: String, amountText: String) {
        val filtered = amountText.filter { it.isDigit() || it == ',' || it == '.' }
        pendingDraftsFlow.value = pendingDraftsFlow.value.map { draft ->
            if (draft.tempId == tempId) draft.copy(amountText = filtered) else draft
        }
    }

    fun updateDraftDate(tempId: String, dateText: String) {
        val filtered = dateText.filter { it.isDigit() || it == '/' }.take(10)
        pendingDraftsFlow.value = pendingDraftsFlow.value.map { draft ->
            if (draft.tempId == tempId) draft.copy(dateFormatted = filtered) else draft
        }
    }

    fun updateDraftCategory(tempId: String, category: ExpenseCategory) {
        pendingDraftsFlow.value = pendingDraftsFlow.value.map { draft ->
            if (draft.tempId == tempId) draft.copy(category = category) else draft
        }
    }

    fun updateDraftType(tempId: String, type: TransactionType) {
        pendingDraftsFlow.value = pendingDraftsFlow.value.map { draft ->
            if (draft.tempId == tempId) draft.copy(type = type) else draft
        }
    }

    fun removeDraft(tempId: String) {
        pendingDraftsFlow.value = pendingDraftsFlow.value.filterNot { it.tempId == tempId }
    }

    fun dismissDrafts() {
        pendingDraftsFlow.value = emptyList()
    }

    fun savePendingDrafts() {
        val drafts = pendingDraftsFlow.value
        val validItems = drafts.mapNotNull { draft ->
            val amount = draft.parsedAmount()
            if (draft.description.isBlank() || amount == null || amount <= 0.0) {
                null
            } else {
                val normalizedDate = ExpenseFormatters.normalizeDate(draft.dateFormatted)
                ExpenseItem(
                    description = draft.description.trim(),
                    amount = amount,
                    category = draft.category,
                    dateFormatted = normalizedDate.formatted,
                    dateSortKey = normalizedDate.sortKey,
                    yearMonthKey = normalizedDate.yearMonth.storageKey,
                    type = draft.type,
                    createdAt = System.currentTimeMillis()
                )
            }
        }

        if (validItems.isEmpty()) return

        val app = getApplication<Application>()
        viewModelScope.launch {
            repository.insertExpenses(validItems)
            pendingDraftsFlow.value = emptyList()

            // Navigate to the saved expense's month so the user sees it immediately
            val firstSavedDate = ExpenseFormatters.normalizeDate(validItems.first().dateFormatted)
            if (selectedMonthFlow.value != firstSavedDate.yearMonth) {
                selectedMonthFlow.value = firstSavedDate.yearMonth
            }
            // If current category filter would hide the newly saved expense, reset to Todos
            val activeFilter = selectedCategoryFlow.value
            if (activeFilter != null && validItems.none { it.category == activeFilter }) {
                selectedCategoryFlow.value = null
            }

            val savedMsg = if (validItems.size == 1) {
                app.getString(R.string.snackbar_saved_single)
            } else {
                app.getString(R.string.snackbar_saved_multiple, validItems.size)
            }
            _snackbarEvents.emit(SnackbarMessage.Info(savedMsg))
        }
    }

    fun deleteExpense(expense: ExpenseItem) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            repository.deleteExpense(expense.id)
            _snackbarEvents.emit(
                SnackbarMessage.DeletedWithUndo(
                    expense = expense,
                    message = app.getString(R.string.snackbar_deleted),
                    undoLabel = app.getString(R.string.snackbar_undo)
                )
            )
        }
    }

    fun restoreExpense(expense: ExpenseItem) {
        viewModelScope.launch {
            repository.restoreExpense(expense)
        }
    }

    private fun computeCategoryShares(
        expenses: List<ExpenseItem>,
        total: Double
    ): List<CategoryShare> {
        if (expenses.isEmpty() || total <= 0.0) return emptyList()
        return expenses
            .groupBy { it.category }
            .map { (category, list) ->
                val catTotal = list.sumOf { it.amount }
                val fraction = (catTotal / total).toFloat().coerceIn(0f, 1f)
                CategoryShare(
                    category = category,
                    total = catTotal,
                    fraction = fraction,
                    percentInt = (fraction * 100f).roundToInt().coerceAtLeast(1)
                )
            }
            .sortedByDescending { it.total }
    }
}
