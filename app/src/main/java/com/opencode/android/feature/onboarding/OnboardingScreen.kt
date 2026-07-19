package com.opencode.android.feature.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.ui.components.OpenCodeBrand
import com.opencode.android.ui.components.SectionCard

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onFinish) { Text(stringResource(R.string.skip)) }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            if (step == 0) {
                OpenCodeBrand()
                Spacer(Modifier.height(28.dp))
                Text(
                    stringResource(R.string.onboarding_welcome_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                OnboardingBullet(stringResource(R.string.onboarding_bullet_local))
                OnboardingBullet(stringResource(R.string.onboarding_bullet_remote))
                OnboardingBullet(stringResource(R.string.onboarding_bullet_voice))
            } else {
                Text(
                    stringResource(R.string.onboarding_choose_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(20.dp))
                SectionCard(modifier = Modifier.clickable(onClick = onFinish)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(stringResource(R.string.onboarding_choose_local_title), fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.onboarding_choose_local_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SectionCard(modifier = Modifier.clickable(onClick = onFinish)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Column {
                            Text(stringResource(R.string.onboarding_choose_pc_title), fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.onboarding_choose_pc_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (step == 0) {
            Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.next))
            }
        }
    }
}

@Composable
private fun OnboardingBullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text("• ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
