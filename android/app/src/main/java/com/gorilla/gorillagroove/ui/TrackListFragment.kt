package com.gorilla.gorillagroove.ui

import android.os.Bundle
import android.view.*
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import androidx.annotation.MainThread
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.gorilla.gorillagroove.R
import com.gorilla.gorillagroove.database.dao.TrackSortType
import com.gorilla.gorillagroove.database.entity.DbTrack
import com.gorilla.gorillagroove.database.entity.DbUser
import com.gorilla.gorillagroove.repository.MainRepository
import com.gorilla.gorillagroove.repository.SelectionOperation
import com.gorilla.gorillagroove.service.GGLog.logDebug
import com.gorilla.gorillagroove.service.GGLog.logInfo
import com.gorilla.gorillagroove.service.TrackChangeEvent
import com.gorilla.gorillagroove.ui.menu.*
import com.gorilla.gorillagroove.util.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_track_list.*
import kotlinx.android.synthetic.main.track_expandable_item.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import javax.inject.Inject


abstract class TrackListFragment<T: TrackReturnable> : GGFragment(R.layout.fragment_track_list), TrackCellAdapter.OnTrackListener {
    private val playerControlsViewModel: PlayerControlsViewModel by viewModels()
    lateinit var trackCellAdapter: TrackCellAdapter<T>

    @Inject
    lateinit var mainRepository: MainRepository

    private val sortOptions = listOf(
        SortMenuOption("Sort by Name", TrackSortType.NAME),
        SortMenuOption("Sort by Play Count", TrackSortType.PLAY_COUNT, initialSortOnTap = SortDirection.DESC),
        SortMenuOption("Sort by Date Added", TrackSortType.DATE_ADDED, initialSortOnTap = SortDirection.DESC),
        SortMenuOption("Sort by Album", TrackSortType.ALBUM),
        SortMenuOption("Sort by Year", TrackSortType.YEAR),
    )

    protected var activeSort = getInitialSort()

    protected var showHidden = false
    protected var albumFilter: String? = null
    protected var artistFilter: String? = null

    protected var showFilterMenu = true

    protected var user: DbUser? = null

    private lateinit var multiselectOptionsMenu: MenuItem
    private lateinit var filterMenu: MenuItem
    protected lateinit var searchMenu: MenuItem
    private lateinit var playMenu: MenuItem
    protected lateinit var editMenu: MenuItem
    protected lateinit var doneMenu: MenuItem

    private val multiselectEnabled get() = trackCellAdapter.showingCheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.getNullableBoolean("SHOW_HIDDEN")?.let { showHidden = it }

        albumFilter = arguments?.getString("ALBUM")
        artistFilter = arguments?.getString("ARTIST")

        // If we are filtering by artist, then sort by album as a default
        if (artistFilter != null) {
            activeSort.sortDirection = SortDirection.NONE

            activeSort = sortOptions
                .first { it.sortType == TrackSortType.ALBUM }
                .also { it.sortDirection = it.initialSortOnTap }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        setupRecyclerView()
        subscribeObservers()

        if (showFilterMenu) {
            setupFilterMenu()
        }

        (requireActivity() as MainActivity).multiselectIcon.setOnClickListener {
            if (!multiselectEnabled) {
                logInfo("User started multiselect")
                setMultiselect(true)
            } else {
                logInfo("User ended multiselect")
                setMultiselect(false)
            }
        }

        requireActivity().multiselectIcon.visibility = View.VISIBLE

        albumFilter?.let { albumFilter ->
            requireActivity().title_tv.text = albumFilter.takeIf { it.isNotEmpty() } ?: "(No Album)"
        }
        artistFilter?.let { artistFilter ->
            if (albumFilter == null) {
                requireActivity().title_tv.text = artistFilter.takeIf { it.isNotEmpty() } ?: "(No Artist)"
            }
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)

        reset()
    }

    @MainThread
    private fun reset() {
        loadingIndicator.isVisible = true

        lifecycleScope.launch(Dispatchers.IO) {
            val tracks = loadTracks()
            withContext(Dispatchers.Main) {
                loadingIndicator?.isVisible = false
                trackCellAdapter.submitList(tracks)

                onTracksLoaded()
            }
        }
    }

    open fun onTracksLoaded() {}

    override fun onBackPressed(): Boolean {
        if (addToPlaylistView.isVisible) {
            addToPlaylistView.close()
            return true
        } else if (recommendTracksView.isVisible) {
            recommendTracksView.close()
            return true
        }
        return super.onBackPressed()
    }

    abstract suspend fun loadTracks(): List<T>

    @MainThread
    protected fun setMultiselect(enabled: Boolean) {
        if (multiselectEnabled == enabled) {
            return
        }

        multiselectOptionsMenu.isVisible = enabled
        playMenu.isVisible = enabled
        trackCellAdapter.showingCheckBox = enabled
        searchMenu.isVisible = !enabled

        trackCellAdapter.notifyDataSetChanged()

        if (enabled) {
            filterMenu.isVisible = false
        } else {
            if (showFilterMenu) {
                filterMenu.isVisible = true
            }

            trackCellAdapter.clearSelectedTracks()
        }
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)

        requireActivity().toolbar.apply {
            navigationIcon = null
            setNavigationOnClickListener {}
        }
    }

    private fun getInitialSort(): SortMenuOption {
        val lastSortType = sharedPreferences.getString("LAST_SORT_TYPE", null)?.let { valueOfOrNull(it) } ?: TrackSortType.NAME
        val lastSortDirection = sharedPreferences.getString("LAST_SORT_DIRECTION", null)?.let { valueOfOrNull(it) } ?: SortDirection.ASC

        return sortOptions.first { it.sortType == lastSortType }.also { it.sortDirection = lastSortDirection }
    }

    private fun setupFilterMenu() {
        popoutMenu.setMenuList(
            listOf(
                *getNavigationOptions(requireView(), LibraryViewType.TRACK, user),
                MenuDivider(),
                *sortOptions.toTypedArray(),
                MenuDivider(),
                CheckedMenuOption(title = "Show Hidden Tracks", false) { showHidden = it.isChecked },
            )
        )

        popoutMenu.onOptionTapped = { menuItem ->
            if (menuItem is SortMenuOption) {
                activeSort = menuItem

                // Keep this around for later so if the user has a preferred sort they aren't having to flip to it all the time
                sharedPreferences.edit()
                    .putString("LAST_SORT_TYPE", menuItem.sortType.toString())
                    .putString("LAST_SORT_DIRECTION", menuItem.sortDirection.toString())
                    .apply()
            }
            reset()
        }
    }

    private fun setupRecyclerView() = track_rv.apply {
        trackCellAdapter = TrackCellAdapter(this@TrackListFragment)
        addItemDecoration(createDivider(context))
        adapter = trackCellAdapter
        layoutManager = LinearLayoutManager(requireContext())
    }

    private fun subscribeObservers() {
        playerControlsViewModel.currentTrackItem.observe(requireActivity(), {
            val mediaId = it.description.mediaId.toString()
            if (mediaId != "") {
                trackCellAdapter.playingTrackId = mediaId
                trackCellAdapter.notifyDataSetChanged()
            }
        })

        lifecycleScope.launch(Dispatchers.Main) {
            playerControlsViewModel.playbackState.collect {
                trackCellAdapter.isPlaying = it.isPlaying
                trackCellAdapter.notifyDataSetChanged()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onTouchDownEvent(event: MotionEvent) = popoutMenu.handleScreenTap(event, this)

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.app_bar_menu, menu)

        multiselectOptionsMenu = menu.findItem(R.id.action_multiselect_menu)
        filterMenu = menu.findItem(R.id.action_filter_menu)
        searchMenu = menu.findItem(R.id.action_search)
        editMenu = menu.findItem(R.id.action_edit_menu)
        doneMenu = menu.findItem(R.id.action_done_menu)
        playMenu = menu.findItem(R.id.action_play_now)

        if (!showFilterMenu) {
            filterMenu.isVisible = false
        }

        val searchView = searchMenu.actionView as SearchView
        searchView.imeOptions = EditorInfo.IME_ACTION_DONE

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                trackCellAdapter.filter.filter(newText)
                return false
            }
        })

        multiselectOptionsMenu.setOnMenuItemClickListener {
            val tracks = trackCellAdapter.getSelectedTracks()
            if (tracks.isEmpty()) {
                lifecycleScope.launch { GGToast.show("Select some tracks first") }
            } else {
                showLibraryActionSheet(tracks)
            }

            true
        }

        playMenu.setOnMenuItemClickListener {
            playNow(trackCellAdapter.getSelectedTracks().asTracks())
            true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        super.onOptionsItemSelected(item)
        return when (item.itemId) {
            R.id.action_filter_menu -> {
                popoutMenu.toggleVisibility(ignoreIfRecent = true)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onTrackClick(position: Int) {
        val clickedTrack = trackCellAdapter.filteredList[position].asTrack()

        logInfo("User tapped track with ID: ${clickedTrack.id}")

        // Accidental plays when you're trying to reorder are annoying
        if (trackCellAdapter.reorderEnabled) {
            logDebug("User tapped a track but reorder was enabled. Not doing anything.")
            return
        }

        LocationService.requestLocationPermissionIfNeeded(requireActivity())

        playerControlsViewModel.playMedia(position, trackCellAdapter.filteredList.asTracks())
    }

    override fun onTrackLongClick(position: Int) {
        if (trackCellAdapter.reorderEnabled) {
            return
        }

        if (!multiselectEnabled) {
            val track = trackCellAdapter.filteredList[position]
            showLibraryActionSheet(track)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun handleTrackChange(event: TrackChangeEvent) {
        if (event.changeType == ChangeType.DELETED) {
            val ids = event.tracks.map { it.id }.toSet()
            logInfo("Removing ${ids.size} track(s) from current track view")

            val newTracks = trackCellAdapter.trackList.filterNot { track -> ids.contains(track.asTrack().id) }

            trackCellAdapter.submitList(newTracks)
        }
    }

    private fun playNow(tracks: List<DbTrack>) {
        mainRepository.setSelectedTracks(tracks, SelectionOperation.PLAY_NOW)
        playerControlsViewModel.playNow(tracks.first())

        setMultiselect(false)
    }

    abstract fun getExtraActionSheetItems(tracks: List<T>): List<ActionSheetItem>

    private fun showLibraryActionSheet(track: T) = showLibraryActionSheet(listOf(track))

    private fun showLibraryActionSheet(tracks: List<T>) {
        ActionSheet(
            requireActivity(),
            listOfNotNull(
                ActionSheetItem("Play Now") {
                    playNow(tracks.asTracks())
                },
                ActionSheetItem("Play Next") {
                    mainRepository.setSelectedTracks(tracks.asTracks(), SelectionOperation.PLAY_NEXT)
                    setMultiselect(false)
                },
                ActionSheetItem("Play Last") {
                    mainRepository.setSelectedTracks(tracks.asTracks(), SelectionOperation.PLAY_LAST)
                    setMultiselect(false)
                },

                *getExtraActionSheetItems(tracks).toTypedArray()
            )
        )
    }

    protected fun editPropertiesActionSheetItem(tracks: List<DbTrack>): ActionSheetItem? {
        // Edit properties is only (currently) available with one track selected. It's a pain to make it work with more. Ticket is in the Trello
        if (tracks.size > 1) {
            return null
        }

        return ActionSheetItem("Edit Properties") {
            // Gotta close the search menu or else the title text won't be visible when this fragment loads for some god forsaken reason and crashes the app
            searchMenu.collapseActionView()

            findNavController().navigate(
                R.id.trackPropertiesFragment,
                bundleOf("KEY_TRACK" to tracks.first()),
            )
        }
    }

    protected fun addToPlaylistActionSheetItem(tracks: List<DbTrack>) = ActionSheetItem("Add to Playlist") {
        lifecycleScope.launch {
            addToPlaylistView.initialize(requireActivity() as MainActivity, tracks)
        }
        setMultiselect(false)
    }

    protected fun recommendActionSheetItem(tracks: List<DbTrack>) = ActionSheetItem("Recommend") {
        lifecycleScope.launch {
            recommendTracksView.initialize(requireActivity() as MainActivity, tracks)
        }
        setMultiselect(false)
    }

    protected fun List<T>.asTracks() = this.map { it.asTrack() }
}

interface TrackReturnable {
    fun asTrack(): DbTrack
}
