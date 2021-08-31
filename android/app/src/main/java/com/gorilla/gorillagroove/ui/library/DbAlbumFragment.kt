package com.gorilla.gorillagroove.ui.library

import android.os.Bundle
import android.view.View
import com.gorilla.gorillagroove.database.GorillaDatabase
import com.gorilla.gorillagroove.database.dao.Album
import com.gorilla.gorillagroove.service.TrackService
import kotlinx.android.synthetic.main.activity_main.*

class DbAlbumFragment : AlbumFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().title_tv.text = "My Library"
    }

    override suspend fun getAlbums(): List<Album> {
        val includeHidden = if (showHidden) null else false

        return GorillaDatabase.trackDao.getDistinctAlbums(
            artistFilter = TrackService.getArtistSqlFilter(artistFilter),
            isHidden = includeHidden,
            inReview = false
        )
    }
}
