package com.gorilla.gorillagroove.ui.login

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.gorilla.gorillagroove.GGApplication
import com.gorilla.gorillagroove.R
import com.gorilla.gorillagroove.service.GGLog.logInfo
import com.gorilla.gorillagroove.service.sync.ServerSynchronizer
import com.gorilla.gorillagroove.service.sync.SyncType
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_first_time_sync.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt


class FirstTimeSyncFragment : Fragment(R.layout.fragment_first_time_sync) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        logInfo("Loading first time sync view")

        requireActivity().apply {
            title_tv.text = "First Time Sync"
            bottomNavigationView.visibility = View.GONE
            playerControlView.visibility = View.GONE
            toolbar.isVisible = false
        }

        lifecycleScope.launch(Dispatchers.IO) {
            doTheSync()
        }
    }

    private suspend fun doTheSync() {
        val syncTypesRemaining = SyncType.values().toMutableSet()

        val context = GGApplication.application

        var extrasSynced = 0
        val extrasToSync = syncTypesRemaining.size - 2 // 2 because tracks and playlists have their own

        // Sync all the things we need to sync. Every time something syncs a page, we invoke this callback to update the UI. When all are synced, we automatically navigate away.
        suspend fun pageSyncHandler(syncType: SyncType, syncedPercent: Double) {
            val percentInt = (syncedPercent * 100).roundToInt()

            withContext(Dispatchers.Main) {
                when (syncType) {
                    SyncType.TRACK -> {
                        libraryPercentage.text = "$percentInt%"
                        libraryProgressBar.progress = percentInt

                        if (percentInt == 100) {
                            libraryPercentage.setTextColor(context.getColor(R.color.ggPrimary))
                        }
                    }
                    SyncType.PLAYLIST_TRACK -> {
                        playlistsPercentage.text = "$percentInt%"
                        playlistsProgressBar.progress = percentInt

                        if (percentInt == 100) {
                            playlistsPercentage.setTextColor(context.getColor(R.color.ggPrimary))
                        }
                    }
                    else -> {
                        if (percentInt == 100) {
                            extrasSynced++
                        }

                        val extrasSyncedPercent = ((extrasSynced.toDouble() / extrasToSync) * 100).roundToInt()

                        extrasPercentage.text = "$extrasSyncedPercent%"
                        extrasProgressBar.progress = extrasSyncedPercent

                        if (extrasSyncedPercent == 100) {
                            extrasPercentage.setTextColor(context.getColor(R.color.ggPrimary))
                        }
                    }
                }

                // If our percent is 100, then we have finished syncing this type and can remove it from the set
                if (percentInt == 100) {
                    syncTypesRemaining.remove(syncType)
                }
            }

            // If all things are removed, then we're done and we can proceed into the main part of the app
            if (syncTypesRemaining.isEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    // Put in a small delay just to give people the satisfaction of seeing everything at 100% before we change the screen
                    delay(1_000)
                    withContext(Dispatchers.Main) {
                        val navOptions = NavOptions.Builder()
                            .setPopUpTo(R.id.libraryTrackFragment, true)
                            .build()

                        findNavController().navigate(R.id.libraryTrackFragment, null, navOptions)
                    }
                }
            }
        }

        ServerSynchronizer.syncWithServer(abortIfRecentlySynced = false, onPageSyncedHandler = ::pageSyncHandler)
    }
}
