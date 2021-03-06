import React, {useContext, useEffect, useState} from 'react';
import {TrackList} from "..";
import {NowPlayingList} from "../now-playing-list/now-playing-list";
import {AlbumArt} from "../album-art/album-art";
import TrackSourceList from "../track-source-list/track-source-list";
import HeaderBar from "../header-bar/header-bar";
import {MusicContext} from "../../services/music-provider";
import {SiteStats} from "../site-stats/site-stats";
import {isLoggedIn} from "../../util";
import {notifyVersion} from "../../services/version";
import {SocketContext} from "../../services/socket-provider";
import PlaybackControls from "../playback-controls/playback-controls";
import {UserContext} from "../../services/user-provider";
import {PlaylistContext} from "../../services/playlist-provider";
import {CenterView} from "../../enums/site-views";
import RemotePlayManagement from "../remote-play/management/remote-play-management";

export default function SiteLayout(props) {
	const [albumArtLink, setAlbumArtLink] = useState(null); // FIXME Really not sure where this should live long term
	const [centerView, setCenterView] = useState(CenterView.TRACKS);

	if (!isLoggedIn()) {
		console.info('Not logged in. Awaiting redirect');
		props.history.push('/login'); // Redirect to the login page now that we logged out

		return <div/>;
	}

	// Is it possible to call functions on these contexts without being affected by their re-renders?
	// All this view does is initialize state. It doesn't need to be re-rendered by the result.
	// If this is possible it'll cut down on the site-wide re-renders
	const musicContext = useContext(MusicContext);
	const socketContext = useContext(SocketContext);
	const userContext = useContext(UserContext);
	const playlistContext = useContext(PlaylistContext);

	useEffect(() => {
		userContext.initialize().catch(error => {
			console.error(error);
			// If we had an error here it PROBABLY means we had a failure to login
			props.history.push('/login');
		});
		socketContext.connectToSocket();
		musicContext.loadSongsForUser();
		playlistContext.loadPlaylists();

		notifyVersion();
	}, []);

	const displayedColumns = musicContext.columnPreferences
		.filter(columnPreference => columnPreference.enabled)
		.map(columnPreference => columnPreference.name);

	return (
		<div className="full-screen border-layout">
			<div className="border-layout-north">
				<HeaderBar/>
			</div>
			<div className="border-layout-west">
				<TrackSourceList
					playlists={playlistContext.playlists}
					centerView={centerView}
					setCenterView={setCenterView}
				/>
			</div>

			<div id="center-view" className="border-layout-center track-list-container p-relative">
				{
					centerView === CenterView.TRACKS
						? <TrackList
							columns={displayedColumns}
							userTracks={musicContext.viewedTracks}
							trackView={true}
						/>
						: <RemotePlayManagement/>
				}
			</div>

			<div className="border-layout-east track-list-container">
				<NowPlayingList
					columns={["#", "Name"]}
					userTracks={musicContext.nowPlayingTracks}
					trackView={false}
				/>
			</div>
			<div className="border-layout-southwest">
				<AlbumArt artLink={albumArtLink}/>
			</div>
			<div className="border-layout-south">
				<PlaybackControls setAlbumArt={setAlbumArtLink}/>
			</div>
			<div className="border-layout-southeast">
				<SiteStats/>
			</div>
		</div>
	)
}
