package com.example.kagenezumi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kagenezumi.ui.theme.KageNezumiTheme

class MainActivity : ComponentActivity() {

    private val permissionsToRequest = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            KageNezumiTheme {
                PermissionsUI(permissionsToRequest)
            }
        }
    }
}

@Composable
fun PermissionsUI(permissions: Array<String>) {
    val context = LocalContext.current
    val missingPermissions = remember {
        permissions.filter {
            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }.toMutableStateList()
    }

    var permissionsGranted by remember { mutableStateOf(missingPermissions.isEmpty()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { !it }.keys
        missingPermissions.clear()
        missingPermissions.addAll(denied)
        permissionsGranted = denied.isEmpty()
        if (!permissionsGranted) {
            Toast.makeText(
                context,
                "Some permissions were denied. Functionality may be limited.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (permissionsGranted) {
                Text(
                    "✅ All permissions granted!",
                    style = MaterialTheme.typography.headlineMedium
                )
            } else {
                Text(
                    "⚠️ Permissions required",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { launcher.launch(permissions) }) {
                    Text("Allow All Permissions")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Missing:\n${missingPermissions.joinToString("\n")}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PermissionsUIPreview() {
    KageNezumiTheme {
        PermissionsUI(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }
}
