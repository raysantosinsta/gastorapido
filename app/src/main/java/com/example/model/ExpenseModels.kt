package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.MonoStep1
import com.example.ui.theme.MonoStep2
import com.example.ui.theme.MonoStep3
import com.example.ui.theme.MonoStep4
import com.example.ui.theme.MonoStep5
import com.example.ui.theme.MonoStep6
import java.text.Normalizer
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

enum class TransactionType(val apiValue: String) {
    GASTO("Gasto"),
    GANHO("Ganho");

    companion object {
        val allApiValues: List<String> = entries.map { it.apiValue }

        fun fromRaw(raw: String?): TransactionType {
            if (raw.isNullOrBlank()) return GASTO
            val lower = raw.trim().lowercase(Locale.ROOT)
            return when {
                lower == "ganho" || lower == "receita" || lower == "entrada" ||
                    lower.contains("recebid") || lower.contains("salari") -> GANHO
                else -> GASTO
            }
        }
    }
}

enum class ExpenseCategory(
    val apiValue: String,
    val icon: ImageVector,
    val barColor: Color
) {
    ALIMENTACAO("Alimentação", Icons.Outlined.Restaurant, MonoStep1),
    TRANSPORTE("Transporte", Icons.Outlined.DirectionsCar, MonoStep2),
    CASA("Casa", Icons.Outlined.Home, MonoStep3),
    LAZER("Lazer", Icons.Outlined.SportsEsports, MonoStep4),
    TRABALHO("Trabalho", Icons.Outlined.WorkOutline, MonoStep5),
    OUTROS("Outros", Icons.Outlined.MoreHoriz, MonoStep6);

    companion object {
        val allApiValues: List<String> = entries.map { it.apiValue }

        fun fromRaw(raw: String?): ExpenseCategory {
            if (raw.isNullOrBlank()) return OUTROS
            val normalized = normalize(raw)
            return entries.firstOrNull { normalize(it.apiValue) == normalized || it.name == normalized.uppercase(Locale.ROOT) }
                ?: when {
                    normalized.contains("aliment") || normalized.contains("comida") || normalized.contains("restaur") -> ALIMENTACAO
                    normalized.contains("transp") || normalized.contains("uber") || normalized.contains("combust") -> TRANSPORTE
                    normalized.contains("casa") || normalized.contains("morad") || normalized.contains("alug") -> CASA
                    normalized.contains("lazer") || normalized.contains("entret") -> LAZER
                    normalized.contains("trab") || normalized.contains("escrit") -> TRABALHO
                    else -> OUTROS
                }
        }

        private fun normalize(value: String): String {
            val decomposed = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .lowercase(Locale.ROOT)
        }
    }
}

data class YearMonthKey(
    val year: Int,
    val month: Int // 1..12
) {
    val storageKey: String
        get() = String.format(Locale.US, "%04d-%02d", year, month)

    fun previous(): YearMonthKey {
        return if (month == 1) YearMonthKey(year - 1, 12) else YearMonthKey(year, month - 1)
    }

    fun next(): YearMonthKey {
        return if (month == 12) YearMonthKey(year + 1, 1) else YearMonthKey(year, month + 1)
    }

    fun formattedPtBr(): String {
        val monthName = PT_BR_MONTHS.getOrElse(month - 1) { "Mês" }
        return "$monthName $year"
    }

    companion object {
        private val PT_BR_MONTHS = listOf(
            "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
        )

        fun current(): YearMonthKey {
            val cal = Calendar.getInstance()
            return YearMonthKey(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1
            )
        }
    }
}

data class ExpenseItem(
    val id: Long = 0L,
    val description: String,
    val amount: Double,
    val category: ExpenseCategory,
    val dateFormatted: String,
    val dateSortKey: Long,
    val yearMonthKey: String,
    val type: TransactionType = TransactionType.GASTO,
    val createdAt: Long = System.currentTimeMillis()
)

data class DraftExpense(
    val tempId: String = UUID.randomUUID().toString(),
    val description: String,
    val amountText: String,
    val category: ExpenseCategory,
    val dateFormatted: String,
    val type: TransactionType = TransactionType.GASTO
) {
    fun parsedAmount(): Double? = ExpenseFormatters.parseAmountInput(amountText)
    fun isValid(): Boolean = description.isNotBlank() && (parsedAmount() ?: 0.0) > 0.0
}

data class CategoryShare(
    val category: ExpenseCategory,
    val total: Double,
    val fraction: Float,
    val percentInt: Int
)

object ExpenseFormatters {
    private val ptBrLocale = Locale("pt", "BR")

    fun formatBrl(amount: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(ptBrLocale)
        return formatter.format(abs(amount))
    }

    fun formatSignedBrl(amount: Double, type: TransactionType): String {
        val prefix = if (type == TransactionType.GANHO) "+ " else "- "
        return "$prefix${formatBrl(amount)}"
    }

    fun formatBalanceBrl(balance: Double): String {
        return when {
            balance < -0.005 -> "-${formatBrl(balance)}"
            balance > 0.005 -> "+${formatBrl(balance)}"
            else -> formatBrl(0.0)
        }
    }

    fun formatAmountForEdit(amount: Double): String {
        return String.format(ptBrLocale, "%.2f", amount)
    }

    fun parseAmountInput(input: String): Double? {
        val cleaned = input.trim()
            .replace("R$", "")
            .replace(" ", "")
        if (cleaned.isEmpty()) return null
        val normalized = if (cleaned.contains(",") && cleaned.contains(".")) {
            // e.g., 1.234,56 -> 1234.56
            cleaned.replace(".", "").replace(",", ".")
        } else if (cleaned.contains(",")) {
            cleaned.replace(",", ".")
        } else {
            cleaned
        }
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    }

    fun todayFormatted(): String {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        return String.format(Locale.US, "%02d/%02d/%04d", day, month, year)
    }

    /**
     * Parses "dd/MM/yyyy" safely and returns normalized (formattedDate, sortKey YYYYMMDD, YearMonthKey).
     * Falls back to today's date if invalid.
     */
    fun normalizeDate(rawDate: String?): NormalizedDate {
        val fallback = todayNormalized()
        if (rawDate.isNullOrBlank()) return fallback
        val match = Regex("""(\d{1,2})\s*[/\-.]\s*(\d{1,2})\s*[/\-.]\s*(\d{2,4})""").find(rawDate.trim())
            ?: return fallback

        val day = match.groupValues[1].toIntOrNull() ?: return fallback
        val month = match.groupValues[2].toIntOrNull() ?: return fallback
        var year = match.groupValues[3].toIntOrNull() ?: return fallback
        if (year < 100) year += 2000

        if (month !in 1..12 || day !in 1..31 || year !in 2000..2100) {
            return fallback
        }

        val cal = Calendar.getInstance().apply {
            isLenient = false
            clear()
            set(year, month - 1, day)
        }
        return try {
            val validDay = cal.get(Calendar.DAY_OF_MONTH)
            val validMonth = cal.get(Calendar.MONTH) + 1
            val validYear = cal.get(Calendar.YEAR)
            val formatted = String.format(Locale.US, "%02d/%02d/%04d", validDay, validMonth, validYear)
            val sortKey = validYear * 10000L + validMonth * 100L + validDay
            NormalizedDate(
                formatted = formatted,
                sortKey = sortKey,
                yearMonth = YearMonthKey(validYear, validMonth)
            )
        } catch (_: Exception) {
            fallback
        }
    }

    private fun todayNormalized(): NormalizedDate {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        return NormalizedDate(
            formatted = String.format(Locale.US, "%02d/%02d/%04d", day, month, year),
            sortKey = year * 10000L + month * 100L + day,
            yearMonth = YearMonthKey(year, month)
        )
    }
}

data class NormalizedDate(
    val formatted: String,
    val sortKey: Long,
    val yearMonth: YearMonthKey
)
