package com.gorilla.gorillagroove.ui.playlists

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.gorilla.gorillagroove.database.GorillaDatabase
import com.gorilla.gorillagroove.database.dao.PlaylistTrackWithTrack
import com.gorilla.gorillagroove.database.entity.DbPlaylist
import com.gorilla.gorillagroove.di.Network
import com.gorilla.gorillagroove.service.GGLog.logError
import com.gorilla.gorillagroove.service.GGLog.logInfo
import com.gorilla.gorillagroove.ui.ActionSheetItem
import com.gorilla.gorillagroove.ui.ActionSheetType
import com.gorilla.gorillagroove.ui.TrackListFragment
import com.gorilla.gorillagroove.util.GGToast
import com.gorilla.gorillagroove.util.showAlertDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_track_list.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class PlaylistTrackFragment : TrackListFragment<PlaylistTrackWithTrack>() {

    private lateinit var playlist: DbPlaylist

    init {
        showFilterMenu = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playlist = arguments?.getSerializable("PLAYLIST") as? DbPlaylist
            ?: throw IllegalArgumentException("A DbPlaylist with key 'PLAYLIST' must be provided to the PlaylistFragment!")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().title_tv.text = playlist.name

        logInfo("Loading PlaylistTracks view with playlist ID: ${playlist.id}")
    }

    override suspend fun loadTracks(): List<PlaylistTrackWithTrack> {
        return GorillaDatabase.playlistTrackDao.findTracksOnPlaylist(playlist.id)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        editMenu.isVisible = true

        editMenu.setOnMenuItemClickListener {
            trackCellAdapter.itemTouchHelper.attachToRecyclerView(view?.track_rv)

            toggleReorderState()

            true
        }

        doneMenu.setOnMenuItemClickListener {
            savePlaylistOrder()

            // Passing in "null" when it's not enabled makes the drag functionality not happen via long press / allows us to disable the drag
            trackCellAdapter.itemTouchHelper.attachToRecyclerView(null)

            toggleReorderState()

            true
        }
    }

    private fun toggleReorderState() {
        val reorderEnabled = !trackCellAdapter.reorderEnabled
        trackCellAdapter.reorderEnabled = reorderEnabled
        searchMenu.isVisible = !reorderEnabled

        editMenu.isVisible = !reorderEnabled
        doneMenu.isVisible = reorderEnabled

        requireActivity().multiselectIcon.isVisible = !reorderEnabled
    }

    private fun savePlaylistOrder() {
        val playlistTracks = trackCellAdapter.trackList.map { it.playlistTrack }

        val request = ReorderPlaylistRequest(
            playlistId = playlist.id,
            playlistTrackIds = playlistTracks.map { it.id }
        )

        // Intentionally global. This is something we don't want to cancel if the fragment goes away
        GlobalScope.launch(Dispatchers.IO) {
            logInfo("Saving new playlist order: $request")

            try {
                Network.api.reorderPlaylist(request)
            } catch (e: Throwable) {
                logError("Failed to save new playlist order!, e")

                GGToast.show("Failed to save playlist order")
                return@launch
            }

            // I'm not concerned about getting an up-to-date reference before editing and saving these.
            // There really isn't much that could be changed by another thread.
            // Not very forward thinking and I could have probably addressed this in less time than writing this comment
            playlistTracks.forEachIndexed { index, playlistTrack ->
                playlistTrack.sortOrder = index
            }

            GorillaDatabase.playlistTrackDao.save(playlistTracks)

            // If things were successful, there doesn't really need to be an alert shown to the user. It doesn't feel like that kind of action
            logInfo("Playlist order saved")
        }
    }

    override fun getExtraActionSheetItems(tracks: List<PlaylistTrackWithTrack>) = listOfNotNull(
        editPropertiesActionSheetItem(tracks.asTracks()),
        recommendActionSheetItem(tracks.asTracks()),
        ActionSheetItem("Remove from Playlist", ActionSheetType.DESTRUCTIVE) {
            showAlertDialog(
                requireActivity(),
                message = "Remove " + (if (tracks.size == 1) "'${tracks.first().asTrack().name}'" else "the selected ${tracks.size} tracks") + "?",
                yesText = "Remove",
                noText = "Cancel",
                yesAction = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val playlistTrackIds = tracks.map { it.playlistTrack.id }

                        logInfo("Deleting playlist tracks: $playlistTrackIds")

                        try {
                            Network.api.deletePlaylistTracks(playlistTrackIds)
                        } catch (e: Throwable) {
                            logError("Failed to remove playlist tracks!", e)
                            GGToast.show("Failed to remove from playlist")

                            return@launch
                        }

                        GorillaDatabase.playlistTrackDao.delete(playlistTrackIds)

                        logInfo("Playlist tracks removed locally")

                        withContext(Dispatchers.Main) {
                            val newTracks = trackCellAdapter.trackList.filterNot { playlistTrackIds.contains(it.playlistTrack.id) }
                            trackCellAdapter.submitList(newTracks)

                            setMultiselect(false)
                        }
                    }
                }
            )
        }
    )
}

data class ReorderPlaylistRequest(
    val playlistId: Long,
    val playlistTrackIds: List<Long>,
)
