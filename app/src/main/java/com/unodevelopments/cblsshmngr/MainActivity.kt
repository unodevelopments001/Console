package com.unodevelopments.cblsshmngr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.unodevelopments.cblsshmngr.ui.AppNav
import com.unodevelopments.cblsshmngr.ui.lock.AppLockGate
import com.unodevelopments.cblsshmngr.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppLockGate {
                    AppNav()
                }
            }
        }
    }
}
