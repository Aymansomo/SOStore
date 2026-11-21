package com.example.myappstore

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myappstore.ui.StoreScreen
import com.example.myappstore.ui.StoreViewModel
import com.example.myappstore.ui.theme.MyAppStoreTheme

class MainActivity : ComponentActivity() {

    private val viewModel: StoreViewModel by viewModels()
    private var packageReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyAppStoreTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                StoreScreen(
                    uiState = uiState,
                    onRefresh = viewModel::refresh,
                    onAction = viewModel::onAction,
                    onToggleChangelog = viewModel::toggleChangelog,
                    onDismissBanner = viewModel::dismissBanner
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerPackageReceiver()
        // Catch installs that finished while the activity (or process) was in the background.
        viewModel.onPackagesChanged()
    }

    override fun onStop() {
        packageReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        packageReceiver = null
        super.onStop()
    }

    private fun registerPackageReceiver() {
        if (packageReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val changedPackage = intent?.data?.schemeSpecificPart
                val isRelevant = changedPackage == null ||
                    changedPackage in AppCatalogConfig.packageNames ||
                    changedPackage == this@MainActivity.packageName

                if (isRelevant) {
                    viewModel.onPackagesChanged()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }

        ContextCompat.registerReceiver(
            this,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        packageReceiver = receiver
    }
}
