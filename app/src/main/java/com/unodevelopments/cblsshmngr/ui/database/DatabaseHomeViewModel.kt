package com.unodevelopments.cblsshmngr.ui.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unodevelopments.cblsshmngr.data.SqlConnection
import com.unodevelopments.cblsshmngr.data.SqlRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DatabaseHomeViewModel(private val repository: SqlRepository) : ViewModel() {
    val connections: StateFlow<List<SqlConnection>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        fun factory(repository: SqlRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { DatabaseHomeViewModel(repository) }
        }
    }
}
