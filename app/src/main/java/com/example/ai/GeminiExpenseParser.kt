package com.example.ai

import android.util.Log
import com.example.model.DraftExpense
import com.example.model.ExpenseCategory
import com.example.model.ExpenseFormatters
import com.example.model.TransactionType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiExpenseParser"
private const val INFERENCE_TIMEOUT_MS = 60_000L

class GeminiExpenseParser {

    private val expenseListSchema = Schema.obj(
        properties = mapOf(
            "gastos" to Schema.array(
                description = "Lista de lançamentos financeiros (gastos ou ganhos) extraídos.",
                items = Schema.obj(
                    properties = mapOf(
                        "descricao" to Schema.string(
                            description = "Descrição curta em português (ex: Posto Shell, Pix para Padaria Central, Pix recebido de João)."
                        ),
                        "valor" to Schema.double(
                            description = "Valor numérico positivo em reais (ex: 42.50)."
                        ),
                        "tipo" to Schema.enumeration(
                            values = TransactionType.allApiValues,
                            description = "Indica se é Gasto (saída/pagamento) ou Ganho (entrada/recebimento)."
                        ),
                        "categoria" to Schema.enumeration(
                            values = ExpenseCategory.allApiValues,
                            description = "Categoria do lançamento."
                        ),
                        "data" to Schema.string(
                            description = "Data no formato dd/MM/yyyy."
                        )
                    )
                )
            )
        )
    )

    // Use ultra-fast gemini-3.5-flash-lite for low-latency structured JSON extraction on text & receipts
    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = expenseListSchema
                temperature = 0.1f
                maxOutputTokens = 512
            }
        )
    }

    suspend fun parseFromText(rawText: String, todayDate: String): List<DraftExpense> =
        withContext(Dispatchers.IO) {
            val prompt = buildString {
                appendLine("Extraia todos os lançamentos financeiros (gastos ou ganhos) do texto em pt-BR. Hoje é $todayDate.")
                appendLine("Regras:")
                appendLine("- Se houver mais de um lançamento (ex: \"42 gasolina e 18 almoço\"), separe em itens distintos em \"gastos\".")
                appendLine("- descricao: curta e clara.")
                appendLine("- valor: número decimal positivo em reais.")
                appendLine("- tipo: \"Ganho\" para entradas/recebimentos/salário/venda/reembolso; \"Gasto\" para compras/pagamentos/despesas.")
                appendLine("- categoria: Alimentação, Transporte, Casa, Lazer, Trabalho ou Outros.")
                appendLine("- data: dd/MM/yyyy (use $todayDate se ausente).")
                appendLine("Texto: ${rawText.trim()}")
            }

            try {
                val response = withTimeout(INFERENCE_TIMEOUT_MS) {
                    model.generateContent(prompt)
                }
                val jsonText = response.text
                    ?: throw IllegalStateException("Resposta vazia do Gemini.")
                parseResponseJson(jsonText, todayDate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse text expense", e)
                throw e
            }
        }

    suspend fun parseFromReceiptJpeg(
        jpegBytes: ByteArray,
        todayDate: String,
        sourceHint: String
    ): List<DraftExpense> = withContext(Dispatchers.IO) {
        val prompt = buildString {
            appendLine("Analise este $sourceHint brasileiro (cupom fiscal ou comprovante Pix) e extraia o lançamento. Hoje é $todayDate.")
            appendLine("Regras:")
            appendLine("- Pix/transferência: extraia valor total, nome da pessoa/estabelecimento na descricao, data dd/MM/yyyy, tipo (\"Ganho\" se for Pix recebido/depósito, ou \"Gasto\" se for Pix enviado/pagamento) e categoria.")
            appendLine("- Cupom fiscal: extraia o valor total pago, estabelecimento na descricao, data dd/MM/yyyy, tipo \"Gasto\" e categoria.")
            appendLine("- categoria: Alimentação, Transporte, Casa, Lazer, Trabalho ou Outros.")
            appendLine("- Se não houver data legível, use $todayDate.")
            appendLine("- Se não for comprovante ou lançamento legível, retorne gastos vazio.")
        }

        try {
            val response = withTimeout(INFERENCE_TIMEOUT_MS) {
                model.generateContent(
                    content {
                        inlineData(jpegBytes, "image/jpeg")
                        text(prompt)
                    }
                )
            }
            val jsonText = response.text
                ?: throw IllegalStateException("Resposta vazia do Gemini.")
            parseResponseJson(jsonText, todayDate)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse receipt image", e)
            throw e
        }
    }

    private fun parseResponseJson(jsonString: String, fallbackDate: String): List<DraftExpense> {
        val root = JSONObject(jsonString)
        val array = root.optJSONArray("gastos") ?: return emptyList()
        val results = mutableListOf<DraftExpense>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val description = item.optString("descricao").trim()
            val amount = item.optDouble("valor", Double.NaN)
            if (description.isEmpty() || !amount.isFinite() || amount <= 0.0) continue

            val category = ExpenseCategory.fromRaw(item.optString("categoria"))
            val type = TransactionType.fromRaw(item.optString("tipo"))
            val rawDate = item.optString("data").ifBlank { fallbackDate }
            val normalizedDate = ExpenseFormatters.normalizeDate(rawDate).formatted

            results.add(
                DraftExpense(
                    description = description,
                    amountText = ExpenseFormatters.formatAmountForEdit(amount),
                    category = category,
                    dateFormatted = normalizedDate,
                    type = type
                )
            )
        }
        return results
    }
}
