package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.TokenRepository
import com.example.ui.TokenFinderScreen
import com.example.ui.TokenFinderViewModel
import com.example.ui.TokenFinderViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize Database & Repository locally
    val database = AppDatabase.getDatabase(applicationContext)
    val repository = TokenRepository(database.validTokenDao())
    
    // Create ViewModel using Factory
    val factory = TokenFinderViewModelFactory(repository)
    val viewModel = ViewModelProvider(this, factory)[TokenFinderViewModel::class.java]

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        TokenFinderScreen(viewModel = viewModel)
      }
    }
  }
}
