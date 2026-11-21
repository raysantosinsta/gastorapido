package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DraftExpense
import com.example.model.ExpenseCategory
import com.example.model.TransactionType
import com.example.ui.theme.ExpenseBorder
import com.example.ui.theme.ExpenseContainer
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeBorder
import com.example.ui.theme.IncomeContainer
import com.example.ui.theme.IncomeGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationBottomSheet(
    drafts: List<DraftExpense>,
    onTypeChange: (String, TransactionType) -> Unit,
    onDescriptionChange: (String, String) -> Unit,
    onAmountChange: (String, String) -> Unit,
    onDateChange: (String, String) -> Unit,
    onCategoryChange: (String, ExpenseCategory) -> Unit,
    onRemoveDraft: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirmSave: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val allValid = drafts.isNotEmpty() && drafts.all { it.isValid() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .testTag("confirmation_bottom_sheet")
        ) {
            val title = if (drafts.size == 1) {
                stringResource(R.string.confirm_title_single)
            } else {
                stringResource(R.string.confirm_title_multiple, drafts.size)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.confirm_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(items = drafts, key = { it.tempId }) { draft ->
                    DraftExpenseEditorCard(
                        draft = draft,
                        canRemove = drafts.size > 1,
                        onTypeChange = { onTypeChange(draft.tempId, it) },
                        onDescriptionChange = { onDescriptionChange(draft.tempId, it) },
                        onAmountChange = { onAmountChange(draft.tempId, it) },
                        onDateChange = { onDateChange(draft.tempId, it) },
                        onCategoryChange = { onCategoryChange(draft.tempId, it) },
                        onRemove = { onRemoveDraft(draft.tempId) }
                    )
                }
            }

            if (!allValid && drafts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.confirm_invalid_values),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag("confirm_cancel_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = stringResource(R.string.confirm_cancel),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Button(
                    onClick = onConfirmSave,
                    enabled = allValid,
                    modifier = Modifier
                        .weight(1.5f)
                        .heightIn(min = 50.dp)
                        .testTag("confirm_save_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    val saveLabel = if (drafts.size == 1) {
                        stringResource(R.string.confirm_save_single)
                    } else {
                        stringResource(R.string.confirm_save_multiple, drafts.size)
                    }
                    Text(
                        text = saveLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun DraftExpenseEditorCard(
    draft: DraftExpense,
    canRemove: Boolean,
    onTypeChange: (TransactionType) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onCategoryChange: (ExpenseCategory) -> Unit,
    onRemove: () -> Unit
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        cursorColor = MaterialTheme.colorScheme.primary
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Prominent Gasto (-) / Ganho (+) selector right after AI analysis
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.confirm_type_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (canRemove) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("draft_remove_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.confirm_remove_item),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TransactionTypeToggleButton(
                    label = stringResource(R.string.type_expense),
                    hint = "- ${stringResource(R.string.type_expense_hint)}",
                    icon = Icons.Rounded.ArrowDownward,
                    selected = draft.type == TransactionType.GASTO,
                    selectedContainer = ExpenseContainer,
                    selectedBorder = ExpenseBorder,
                    selectedContent = ExpenseCoral,
                    onClick = { onTypeChange(TransactionType.GASTO) },
                    testTag = "draft_type_gasto",
                    modifier = Modifier.weight(1f)
                )

                TransactionTypeToggleButton(
                    label = stringResource(R.string.type_income),
                    hint = "+ ${stringResource(R.string.type_income_hint)}",
                    icon = Icons.Rounded.ArrowUpward,
                    selected = draft.type == TransactionType.GANHO,
                    selectedContainer = IncomeContainer,
                    selectedBorder = IncomeBorder,
                    selectedContent = IncomeGreen,
                    onClick = { onTypeChange(TransactionType.GANHO) },
                    testTag = "draft_type_ganho",
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = draft.description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.confirm_field_description)) },
                singleLine = true,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("draft_description_field")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = draft.amountText,
                    onValueChange = onAmountChange,
                    label = { Text(stringResource(R.string.confirm_field_amount)) },
                    prefix = {
                        Text(
                            text = if (draft.type == TransactionType.GANHO) "+ R$ " else "- R$ ",
                            color = if (draft.type == TransactionType.GANHO) IncomeGreen else ExpenseCoral
                        )
                    },
                    singleLine = true,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("draft_amount_field")
                )

                OutlinedTextField(
                    value = draft.dateFormatted,
                    onValueChange = onDateChange,
                    label = { Text(stringResource(R.string.confirm_field_date)) },
                    singleLine = true,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("draft_date_field")
                )
            }

            Text(
                text = stringResource(R.string.confirm_field_category),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = ExpenseCategory.entries,
                    key = { it.name }
                ) { category ->
                    MonochromeFilterPill(
                        label = category.apiValue,
                        icon = category.icon,
                        selected = draft.category == category,
                        onClick = { onCategoryChange(category) },
                        testTag = "draft_category_${category.name.lowercase()}"
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionTypeToggleButton(
    label: String,
    hint: String,
    icon: ImageVector,
    selected: Boolean,
    selectedContainer: Color,
    selectedBorder: Color,
    selectedContent: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        selectedContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = if (selected) {
        selectedBorder
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val contentColor = if (selected) {
        selectedContent
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 52.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = contentColor
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = if (selected) 0.9f else 0.7f)
                )
            }
        }
    }
}
