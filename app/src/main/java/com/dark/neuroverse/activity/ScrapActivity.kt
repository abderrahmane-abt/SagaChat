package com.dark.neuroverse.activity

import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.ui.theme.Coral
import com.dark.neuroverse.ui.theme.Mint
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.mp.data_hub_lib.DataNativeLib
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ScrapActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val lib = DataNativeLib()
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val vecxFile = File(downloadsDir, "ai/embeddings.vecx")

        var licenseValid by mutableStateOf(false)
        var licenseText by mutableStateOf("")
        var mItems by mutableStateOf(listOf<JSONObject>())

        // Load vecx in background
        try {
            if (vecxFile.exists()) {
                val ok =
                    lib.loadVecx(vecxFile.absolutePath, "use-a-real-key-from-keystore-or-server")
                if (ok) {
                    val lJson = lib.getEntity("l")
                    val mJson = lib.getEntity("m")

                    val lObj = JSONObject(lJson)
                    licenseText = lObj.optString("license_text", "")
                    licenseValid = lObj.optString("license_type") == "proprietary"

                    val mArr = JSONArray(mJson)
                    val tempList = mutableListOf<JSONObject>()
                    for (i in 0 until mArr.length()) {
                        tempList.add(mArr.getJSONObject(i))
                    }
                    mItems = tempList
                }
            }
        } catch (e: Exception) {
            licenseValid = false
            licenseText = "Error loading license: ${e.message}"
        }

        setContent {
            NeuroVerseTheme {
                Scaffold(containerColor = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.fillMaxSize().padding(it)) {

                        // License Status
                        Card(
                            modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                                containerColor = if (licenseValid) Mint.copy(0.1f) else Coral.copy(
                                    0.1f
                                ),
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = if (licenseValid) "License Valid ✅" else "License Invalid ❌",
                                    color = if (licenseValid) Mint else Coral,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = licenseText, style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // m Items List
                        Text(
                            text = "Documents", style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(mItems) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "ID: ${item.getString("id")}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Category: ${item.getString("category")}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                        Text(
                                            text = "Length: ${item.getInt("length")}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
