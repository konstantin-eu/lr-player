package com.example.lrplayer

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.DialogFragment

class RecentFilesDialogFragment : DialogFragment() {

    private var recentFiles: LinkedHashMap<String, String> = LinkedHashMap()

    companion object {
        const val TAG = "RecentFilesDialogFragment"
        private const val ARG_RECENT_FILES = "arg_recent_files"

        fun newInstance(recentFiles: LinkedHashMap<String, String>): RecentFilesDialogFragment {
            val fragment = RecentFilesDialogFragment()
            val args = Bundle().apply {
                putSerializable(ARG_RECENT_FILES, recentFiles)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            recentFiles = it.getSerializable(ARG_RECENT_FILES) as LinkedHashMap<String, String>
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val fileNames = recentFiles.keys.toTypedArray()

        return AlertDialog.Builder(requireContext())
            .setTitle("Select from Recent")
            .setItems(fileNames) { dialog, which ->
                val selectedFileName = fileNames[which]
                val selectedFilePath = recentFiles[selectedFileName]
                Log.d(TAG, "Selected: $selectedFileName, Path: $selectedFilePath")

                (activity as? MainActivity)?.handleRecentFileSelected(selectedFilePath)
            }
            .create()
    }
}