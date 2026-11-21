package com.example

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ai.configureAppCheck
import com.example.ui.ExpenseViewModel
import com.example.ui.GastoRapidoScreen
import com.example.ui.theme.GastoRapidoTheme

private const val EXTRA_SHARE_CONSUMED = "gasto_rapido_share_consumed"

class MainActivity : ComponentActivity() {

    private val viewModel: ExpenseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        // STEP 2: Full dark edge-to-edge system bars
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // STEP 3: Handle incoming Share Intent (Pix image or PDF) if launched via Share menu
        handleShareIntent(intent)

        setContent {
            GastoRapidoTheme {
                GastoRapidoScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return
        if (incomingIntent.action != Intent.ACTION_SEND) return
        if (incomingIntent.getBooleanExtra(EXTRA_SHARE_CONSUMED, false)) return

        val sharedUri = extractSharedStreamUri(incomingIntent) ?: return
        val mimeType = incomingIntent.type
        incomingIntent.putExtra(EXTRA_SHARE_CONSUMED, true)

        viewModel.analyzeMediaUri(
            uri = sharedUri,
            explicitMimeType = mimeType,
            isFromShareIntent = true
        )
    }

    private fun extractSharedStreamUri(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        } ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }
}
