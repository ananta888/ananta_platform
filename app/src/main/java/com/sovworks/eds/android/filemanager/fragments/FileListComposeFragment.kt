package com.sovworks.eds.android.filemanager.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.sovworks.eds.android.filemanager.FileManagerFragment
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.filemanager.ui.FileListScreen
import com.sovworks.eds.android.filemanager.viewmodel.FileListEvent
import com.sovworks.eds.android.filemanager.viewmodel.FileListViewModel
import com.sovworks.eds.locations.Location

class FileListComposeFragment : Fragment(), FileManagerFragment {

    private lateinit var viewModel: FileListViewModel

    override fun onBackPressed(): Boolean {
        if (viewModel.state.value.selectionMode) {
            viewModel.onEvent(FileListEvent.ClearSelection, requireContext())
            viewModel.onEvent(FileListEvent.ToggleSelectionMode, requireContext())
            return true
        }
        if (viewModel.canNavigateBack()) {
            viewModel.onEvent(FileListEvent.NavigateBack, requireContext())
            return true
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[FileListViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                FileListScreen(
                    viewModel = viewModel,
                    context = requireContext(),
                    onMenuClick = {
                        (activity as? FileManagerActivity)?.drawerController?.openDrawer()
                    }
                )
            }
        }
    }

    fun setLocation(location: Location) {
        viewModel.onEvent(FileListEvent.LocationChanged(location), requireContext())
    }

    companion object {
        @JvmStatic
        fun newInstance(): FileListComposeFragment {
            return FileListComposeFragment()
        }
    }
}
