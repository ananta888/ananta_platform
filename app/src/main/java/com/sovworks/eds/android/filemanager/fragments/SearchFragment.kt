package com.sovworks.eds.android.filemanager.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SearchView
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.sovworks.eds.android.R
import com.sovworks.eds.android.network.SearchManager
import com.sovworks.eds.android.network.SearchResponse
import com.sovworks.eds.android.network.SharedFile

class SearchFragment : Fragment() {

    private lateinit var searchView: SearchView
    private lateinit var resultsList: ListView
    private lateinit var progress: ProgressBar
    private lateinit var noResultsText: TextView
    private lateinit var trustSeekBar: SeekBar
    private lateinit var trustValueText: TextView
    private lateinit var adapter: SearchResultsAdapter
    private val results = mutableListOf<Pair<String, SharedFile>>()

    private val searchListener: (SearchResponse) -> Unit = { response ->
        activity?.runOnUiThread {
            handleSearchResponse(response)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)
        searchView = view.findViewById(R.id.search_view)
        resultsList = view.findViewById(R.id.search_results_list)
        progress = view.findViewById(R.id.search_progress)
        noResultsText = view.findViewById(R.id.no_results_text)
        trustSeekBar = view.findViewById(R.id.trust_filter_seekbar)
        trustValueText = view.findViewById(R.id.trust_value_text)

        adapter = SearchResultsAdapter(results)
        resultsList.adapter = adapter

        trustSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.toDouble() / 10.0
                trustValueText.text = value.toString()
                SearchManager.getInstance(requireContext()).setMinTrustLevel(value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    performSearch(query)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })

        SearchManager.getInstance(requireContext()).addSearchListener(searchListener)

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        SearchManager.getInstance(requireContext()).removeSearchListener(searchListener)
    }

    private fun performSearch(query: String) {
        results.clear()
        adapter.notifyDataSetChanged()
        progress.visibility = View.VISIBLE
        noResultsText.visibility = View.GONE
        SearchManager.getInstance(requireContext()).search(query)
    }

    private fun handleSearchResponse(response: SearchResponse) {
        progress.visibility = View.GONE
        val newResults = response.results.map { response.peerId to it }
        results.addAll(newResults)
        adapter.notifyDataSetChanged()
        if (results.isEmpty()) {
            noResultsText.visibility = View.VISIBLE
        } else {
            noResultsText.visibility = View.GONE
        }
    }

    inner class SearchResultsAdapter(private val items: List<Pair<String, SharedFile>>) : BaseAdapter() {

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(parent?.context)
                .inflate(R.layout.item_search_result, parent, false)
            
            val fileName: TextView = view.findViewById(R.id.file_name)
            val peerInfo: TextView = view.findViewById(R.id.peer_info)
            val downloadButton: View = view.findViewById(R.id.download_button)
            
            val (peerId, file) = items[position]
            fileName.text = file.name
            peerInfo.text = "Peer: ${peerId.take(8)}... | Size: ${file.size} bytes"

            downloadButton.setOnClickListener {
                SearchManager.getInstance(requireContext()).requestFile(peerId, file.name)
            }
            
            return view
        }
    }

    companion object {
        @JvmStatic
        fun newInstance() = SearchFragment()
    }
}
