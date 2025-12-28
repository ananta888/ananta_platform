package com.sovworks.eds.android.ui.transfers

import androidx.lifecycle.ViewModel
import com.sovworks.eds.android.transfer.FileTransferManager
import kotlinx.coroutines.flow.StateFlow

class FileTransferDashboardViewModel : ViewModel() {
    val transfers: StateFlow<List<com.sovworks.eds.android.transfer.FileTransferEntry>> =
        FileTransferManager.state

    fun pauseTransfer(transferId: String) {
        FileTransferManager.pauseTransfer(transferId)
    }

    fun resumeTransfer(transferId: String) {
        FileTransferManager.resumeTransfer(transferId)
    }
}
