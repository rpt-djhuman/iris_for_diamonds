package com.nervesparks.iris.ui.components

import android.app.DownloadManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nervesparks.iris.Downloadable
import com.nervesparks.iris.MainViewModel



@Composable
fun DownloadModal(viewModel: MainViewModel, dm: DownloadManager, models: List<Downloadable>) {
    Dialog(onDismissRequest = { if (models.any { it.destination.exists() }) viewModel.showModal = false }) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF233340),
            modifier = Modifier
                .padding(10.dp)
                .height(300.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Check if any models are already downloaded
                val downloadedModels = models.filter { it.destination.exists() }
                val needsDownload = downloadedModels.isEmpty()

                HeaderText(if (needsDownload) "Download Required" else "Load a Model")

                if (needsDownload) {
                    HeaderText("Don't close or minimize the app!")
                    HeaderModelText("Download at least 1 model")

                    LazyColumn(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(models.filter { !it.destination.exists() }) { model ->
                            DownloadCard(viewModel, dm, model)
                        }
                    }
                } else {
                    HeaderModelText("Select a model to load")

                    LazyColumn(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(downloadedModels) { model ->
                            LoadModelCard(viewModel, model)
                        }
                    }
                }

                // Add a back button to dismiss the modal
                TextButton(
                    onClick = { viewModel.showModal = false },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("Back", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun LoadModelCard(viewModel: MainViewModel, model: Downloadable) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xff0f172a),
            contentColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = model.name,
                color = Color(0xFFbbbdbf)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.load(model.destination.path, userThreads = viewModel.user_thread.toInt())
                    viewModel.showModal = false
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB)
                )
            ) {
                Text("Load Model", color = Color.White)
            }
        }
    }
}

@Composable
private fun HeaderModelText(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.W900,
        fontSize = 18.sp,
        color = Color.White,
        modifier = Modifier
            .padding(top = 14.dp)
    )
}

@Composable
private fun HeaderText(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
}

@Composable
private fun DownloadCard(viewModel: MainViewModel, dm: DownloadManager, model: Downloadable) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xff0f172a),
            contentColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = model.name,
                color = Color(0xFFbbbdbf)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Downloadable.Button(viewModel, dm, model)
        }
    }
}
