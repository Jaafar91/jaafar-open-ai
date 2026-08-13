package com.jaafar.remoteconfig

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.jaafar.remoteconfig.fontcreator.FontCreatorApp
import com.jaafar.remoteconfig.fontcreator.FontCreatorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(this)[FontCreatorViewModel::class.java]
        setContent { FontCreatorApp(viewModel) }
    }
}
