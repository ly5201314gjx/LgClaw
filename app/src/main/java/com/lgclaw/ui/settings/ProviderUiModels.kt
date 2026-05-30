package com.lgclaw.ui

import com.lgclaw.config.AppLimits
import com.lgclaw.providers.ProviderCatalog
import com.lgclaw.providers.ProviderProtocol

/**
 * Editable provider configuration shown in the settings UI.
 */
data class UiProviderConfig(
    val id: String,
    val providerName: String = AppLimits.DEFAULT_PROVIDER,
    val customName: String = "",
    val providerProtocol: ProviderProtocol = ProviderCatalog.defaultProtocol(AppLimits.DEFAULT_PROVIDER),
    val apiKey: String = "",
    val model: String = ProviderCatalog.defaultModel(
        AppLimits.DEFAULT_PROVIDER,
        ProviderCatalog.defaultProtocol(AppLimits.DEFAULT_PROVIDER)
    ),
    val equippedModels: List<String> = emptyList(),
    val baseUrl: String = ProviderCatalog.defaultBaseUrl(
        AppLimits.DEFAULT_PROVIDER,
        ProviderCatalog.defaultProtocol(AppLimits.DEFAULT_PROVIDER)
    ),
    val enabled: Boolean = false
)
