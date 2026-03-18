package com.githow.links.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import com.githow.links.data.database.LinksDatabase

class SmsViewModel(application: Application) : AndroidViewModel(application) {

    private val rawSmsDao = LinksDatabase.getDatabase(application).rawSmsDao()

    // All raw SMS messages (LiveData for Compose observeAsState)
    val allRawSms = rawSmsDao.getAllRawSms().asLiveData()

    // Count of PARSE_ERROR messages — used for badge on "View Unparsed" button
    val parseErrorCount = rawSmsDao.getParseErrorCount().asLiveData()

    // Count of all messages needing attention (PARSE_ERROR + UNPROCESSED + MANUAL_REVIEW)
    val unparsedCount = rawSmsDao.getUnparsedCount().asLiveData()
}