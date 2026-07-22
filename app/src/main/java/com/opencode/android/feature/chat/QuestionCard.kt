package com.opencode.android.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.android.R

@Composable
fun QuestionCard(
    question: PendingQuestionUi,
    onAnswerSelected: (String, Int, String) -> Unit,
    onSubmit: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("question-${question.request.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            question.request.questions.forEachIndexed { index, prompt ->
                val selectedAnswers = question.selectedAnswers.getOrElse(index) { emptyList() }
                val optionLabels = prompt.options.map { it.label }.toSet()
                val fallbackText = selectedAnswers.lastOrNull { it !in optionLabels }.orEmpty()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    prompt.header?.takeIf { it.isNotBlank() }?.let { header ->
                        Text(
                            text = header,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = prompt.question,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    prompt.options.forEach { option ->
                        val selected = option.label in selectedAnswers
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAnswerSelected(question.request.id, index, option.label) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (question.request.multiple) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { onAnswerSelected(question.request.id, index, option.label) }
                                )
                            } else {
                                RadioButton(
                                    selected = selected,
                                    onClick = { onAnswerSelected(question.request.id, index, option.label) }
                                )
                            }
                            Column {
                                Text(option.label, style = MaterialTheme.typography.bodyMedium)
                                option.description?.takeIf { it.isNotBlank() }?.let { description ->
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (prompt.options.isEmpty() || prompt.placeholder != null) {
                        OutlinedTextField(
                            value = fallbackText,
                            onValueChange = { onAnswerSelected(question.request.id, index, it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(prompt.placeholder ?: stringResource(R.string.message_hint))
                            },
                            singleLine = !question.request.multiple
                        )
                    }
                }
            }

            question.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(2.dp))

            Button(
                onClick = { onSubmit(question.request.id) },
                enabled = question.canSubmit && !question.isSubmitting,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("question-submit-${question.request.id}")
            ) {
                if (question.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.continue_label))
                }
            }
        }
    }
}
