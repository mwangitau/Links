package com.githow.links.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import com.githow.links.data.database.LinksDatabase

class SmsViewModel(application: Application) : AndroidViewModel(application) {

    private val rawSmsDao = LinksDatabase.getDatabase(application).rawSmsDao()

    // Convert Flow to LiveData for Compose observeAsState
    val allRawSms = rawSmsDao.getAllRawSms().asLiveData()
}