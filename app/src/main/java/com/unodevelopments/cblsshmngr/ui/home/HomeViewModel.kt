package com.unodevelopments.cblsshmngr.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unodevelopments.cblsshmngr.data.ConnectionRepository
import com.unodevelopments.cblsshmngr.data.SshConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: ConnectionRepository) : ViewModel() {
    val connections: StateFlow<List<SshConnection>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        fun factory(repository: ConnectionRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}
