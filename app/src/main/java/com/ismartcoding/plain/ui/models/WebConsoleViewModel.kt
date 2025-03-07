package com.ismartcoding.plain.ui.models

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.lib.helpers.CoroutinesHelper.withIO
import com.ismartcoding.plain.BuildConfig
import com.ismartcoding.plain.R
import com.ismartcoding.plain.api.HttpClientManager
import com.ismartcoding.plain.data.preference.WebPreference
import com.ismartcoding.plain.features.IgnoreBatteryOptimizationEvent
import com.ismartcoding.plain.features.StartHttpServerEvent
import com.ismartcoding.plain.helpers.AppHelper
import com.ismartcoding.plain.powerManager
import com.ismartcoding.plain.ui.helpers.DialogHelper
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch

class WebConsoleViewModel : ViewModel() {
    fun enableWebConsole(
        context: Context,
        enable: Boolean,
    ) {
        viewModelScope.launch {
            withIO { WebPreference.putAsync(context, enable) }
            if (enable) {
                requestIgnoreBatteryOptimization()
                sendEvent(StartHttpServerEvent())
            }
        }
    }

    fun dig(
        context: Context,
        httpPort: Int,
    ) {
        viewModelScope.launch {
            val client = HttpClientManager.httpClient()
            DialogHelper.showLoading()
            val errorMessage = context.getString(R.string.http_server_error)
            try {
                val r = withIO { client.get("http://127.0.0.1:$httpPort/health_check") }
                DialogHelper.hideLoading()
                if (r.status == HttpStatusCode.OK) {
                    DialogHelper.showConfirmDialog(context, context.getString(R.string.confirm), context.getString(R.string.http_server_ok))
                } else {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(context.getString(R.string.error))
                        .setMessage(errorMessage)
                        .setPositiveButton(R.string.ok) { _, _ ->
                        }
                        .setNegativeButton(R.string.relaunch_app) { _, _ ->
                            AppHelper.relaunch(context)
                        }
                        .create()
                        .show()
                }
            } catch (ex: Exception) {
                DialogHelper.hideLoading()
                MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.error))
                    .setMessage(errorMessage)
                    .setPositiveButton(R.string.ok) { _, _ ->
                    }
                    .setNegativeButton(R.string.relaunch_app) { _, _ ->
                        AppHelper.relaunch(context)
                    }
                    .create()
                    .show()
            }
        }
    }

    fun requestIgnoreBatteryOptimization() {
        val packageName = BuildConfig.APPLICATION_ID
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            sendEvent(IgnoreBatteryOptimizationEvent())
        }
    }
}
