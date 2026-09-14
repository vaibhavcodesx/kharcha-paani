package com.vaibhav.kharchapaani.ui.groups

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.remote.SupabaseClientProvider
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.data.sync.SupabaseGroupMembershipRemote
import com.vaibhav.kharchapaani.data.sync.SyncScheduler

/** Account-scoped owner of [SharedGroupsController]; lives in the signed-in account's ViewModelStore. */
class SharedGroupsViewModel(
    application: Application,
    db: AppDatabase,
    userId: String,
) : AndroidViewModel(application) {

    private val requestSync = { SyncScheduler.requestSync(application, userId) }

    val controller = SharedGroupsController(
        scope = viewModelScope,
        repository = LedgerRepository(db, onLocalTransactionChange = requestSync, selfProfileId = userId),
        remote = SupabaseGroupMembershipRemote(SupabaseClientProvider.get(application)),
        selfProfileId = userId,
        requestSync = requestSync,
    )

    companion object {
        fun factory(application: Application, db: AppDatabase, userId: String): ViewModelProvider.Factory =
            viewModelFactory { initializer { SharedGroupsViewModel(application, db, userId) } }
    }
}
