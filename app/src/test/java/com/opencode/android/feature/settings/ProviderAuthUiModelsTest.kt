package com.opencode.android.feature.settings

import com.opencode.android.core.api.ProviderAuthMethod
import com.opencode.android.core.api.ProviderAuthPrompt
import com.opencode.android.core.api.ProviderAuthWhen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAuthUiModelsTest {
    private val method = ProviderAuthMethod(
        type = "oauth",
        label = "Workspace login",
        prompts = listOf(
            ProviderAuthPrompt(
                type = "select",
                key = "region",
                message = "Region"
            ),
            ProviderAuthPrompt(
                type = "text",
                key = "tenant",
                message = "Tenant",
                whenCondition = ProviderAuthWhen("region", "eq", "us")
            )
        )
    )

    @Test
    fun `only visible prompts are required`() {
        val nonUs = ProviderAuthDialogState(
            providerId = "custom",
            providerName = "Custom",
            methods = listOf(method),
            methodIndex = 0,
            inputs = mapOf("region" to "eu")
        )
        val usWithoutTenant = nonUs.copy(inputs = mapOf("region" to "us"))
        val usComplete = usWithoutTenant.copy(inputs = mapOf("region" to "us", "tenant" to "acme"))

        assertTrue(nonUs.promptsComplete)
        assertFalse(usWithoutTenant.promptsComplete)
        assertTrue(usComplete.promptsComplete)
    }
}
