package org.android.starter

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.android.starter.network.GlobalNetworkResponseV1
import org.android.starter.network.NetworkV1

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Button(
                    onClick = { sayHello() }
                ) {
                    Text("Say Hello")
                }
            }
        }
    }

    private suspend fun showToast(msg: String) = withContext(Dispatchers.Main) {
        Toast.makeText(
            this@MainActivity,
            msg,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun sayHello() {
        this.lifecycleScope.launch {
            when (val resp = NetworkV1.hello("Hello")) {
                is GlobalNetworkResponseV1.Success -> {
                    showToast("from server: ${resp.response}")
                }

                is GlobalNetworkResponseV1.ServerFailed -> {
                    showToast("server error: ${resp.responseCode}, ${resp.responseMsg}")
                }

                is GlobalNetworkResponseV1.LocalFailed -> {
                    showToast(resp.message)
                    Log.e("MainActivity", "sayHello: ${resp.message}", resp.error)
                }
            }
        }
    }
}