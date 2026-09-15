package com.jasensic.mydrive.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.jasensic.mydrive.data.SyncWorker
import com.jasensic.mydrive.domain.LocalMediaStore
import com.jasensic.mydrive.domain.ServerDiscovery
import com.jasensic.mydrive.domain.SyncFilesUseCase
import com.jasensic.mydrive.domain.SyncStateRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val server: String = "searching…",
    val lastSync: String? = null,
    val files: List<String> = emptyList(),
    val progress: String? = null,
    val error: String? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val discovery: ServerDiscovery,
    private val syncFiles: SyncFilesUseCase,
    private val local: LocalMediaStore,
    private val stateRepo: SyncStateRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val server = runCatching { discovery.find() }.getOrNull()
            _ui.value = _ui.value.copy(
                server = server?.let { "${it.host}:${it.port}" } ?: "not found",
                lastSync = stateRepo.lastSyncAt(),
                files = local.knownIds().toList(),
            )
        }
    }

    fun sync(username: String, password: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(progress = "Syncing…", error = null)
            runCatching { syncFiles.execute(username.ifBlank { null }, password.ifBlank { null }) }
                .onSuccess { manifest ->
                    _ui.value = _ui.value.copy(
                        progress = null,
                        lastSync = manifest.generatedAt,
                        files = local.knownIds().toList(),
                    )
                }
                .onFailure {
                    _ui.value = _ui.value.copy(progress = null, error = it.message)
                }
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val state by vm.ui.collectAsState()
                    var user by remember { mutableStateOf("admin") }
                    var pass by remember { mutableStateOf("") }
                    Column(Modifier.padding(16.dp)) {
                        Text("my-drive", style = MaterialTheme.typography.headlineSmall)
                        Text("Server: ${state.server}")
                        Text("Last sync: ${state.lastSync ?: "never"}")
                        OutlinedTextField(user, { user = it }, label = { Text("Username") })
                        OutlinedTextField(pass, { pass = it }, label = { Text("Password") })
                        Button(onClick = {
                            vm.sync(user, pass)
                            WorkManager.getInstance(this@MainActivity).enqueue(
                                OneTimeWorkRequestBuilder<SyncWorker>()
                                    .setInputData(workDataOf("username" to user, "password" to pass))
                                    .build(),
                            )
                        }) { Text("Sync now") }
                        state.progress?.let { LinearProgressIndicator(modifier = Modifier.padding(top = 8.dp)) }
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        LazyColumn {
                            items(state.files) { Text(it, modifier = Modifier.padding(vertical = 4.dp)) }
                        }
                    }
                }
            }
        }
    }
}
