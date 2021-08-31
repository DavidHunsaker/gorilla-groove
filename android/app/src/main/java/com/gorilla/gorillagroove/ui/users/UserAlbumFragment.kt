package com.gorilla.gorillagroove.ui.users

import android.os.Bundle
import android.view.View
import com.gorilla.gorillagroove.database.dao.Album
import com.gorilla.gorillagroove.database.entity.DbUser
import com.gorilla.gorillagroove.di.Network
import com.gorilla.gorillagroove.service.TrackService
import com.gorilla.gorillagroove.ui.library.AlbumFragment
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class UserAlbumFragment : AlbumFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        user = requireArguments().getSerializable("USER") as DbUser

        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().title_tv.text = "${user!!.name}'s Library"
    }

    override suspend fun getAlbums(): List<Album> {
        val trackResponses = Network.api.getTracks(userId = user!!.id, showHidden = showHidden).content

        return trackResponses
            .filter { TrackService.passesArtistFilter(artistFilter, it) }
            .groupBy { it.album } // We only need one entry per album. It doesn't matter which one
            .mapValues { it.value.first() } // Remove the one entry from the list so it is just standalone
            .values // Now that they're grouped, we no longer need the group name that was used as the key
            .map { Album(name = it.album, trackId = it.id) }
            .sortedBy { it.name.toLowerCase(Locale.getDefault()) }
    }
}
