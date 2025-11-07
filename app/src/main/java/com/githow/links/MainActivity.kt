package com.githow.links

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.githow.links.data.database.LinksDatabase
import com.githow.links.ui.theme.LINKSTheme
import com.githow.links.utils.MpesaParser
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LINKSTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "LINKS",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        // Test database initialization
        lifecycleScope.launch {
            try {
                val db = LinksDatabase.getDatabase(applicationContext)
                Log.d("LINKS", "✅ Database initialized successfully!")

                // Test parser
                val testSms = """TJRBT8H7LR Confirmed.on 27/10/25 at 2:27 PMKsh150.00 received from 254757896068 SIMON NGUNDI WANJIRU. New Account balance is Ksh723,426.00. Transaction cost, Ksh0.00."""

                val transaction = MpesaParser.parseTransaction(testSms)
                if (transaction != null) {
                    Log.d("LINKS", "✅ Parser works! Code: ${transaction.mpesa_code}, Amount: Ksh${transaction.amount}")
                } else {
                    Log.e("LINKS", "❌ Parser failed!")
                }

            } catch (e: Exception) {
                Log.e("LINKS", "❌ Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LINKSTheme {
        Greeting("LINKS")
    }
}