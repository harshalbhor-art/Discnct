package com.discnct.app.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import com.discnct.app.MainActivity
import com.discnct.app.ui.theme.DiscnctTheme

/**
 * Level 3: registered as a Home-category activity so Discnct can be picked as the OS default
 * launcher (see LauncherStatus — that choice can only be made by the user in system settings,
 * never forced from here). Tapping the "DISCNCT HOME" label opens MainActivity so the block
 * list and Level 3 toggle stay reachable without needing a separate app icon search.
 */
class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DiscnctTheme {
                // A Home screen has nowhere for the physical Back button to go.
                BackHandler {}
                LauncherScreen(onOpenSettings = { startActivity(Intent(this, MainActivity::class.java)) })
            }
        }
    }
}
