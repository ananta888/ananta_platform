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
import com.sovworks.eds.android.navigation.NavigationViewModel
import com.sovworks.eds.locations.Location

class FileListComposeFragment : Fragment(), FileManagerFragment {

    private lateinit var viewModel: FileListViewModel
    private lateinit var navigationViewModel: NavigationViewModel

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
        viewModel = ViewModelProvider(requireActivity())[FileListViewModel::class.java]
        navigationViewModel = ViewModelProvider(requireActivity())[NavigationViewModel::class.java]
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
                    navigationViewModel = navigationViewModel,
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
