import React, {useEffect, useState, useContext} from "react";
import {MusicFilterContext} from "../../../services/music-filter-provider";
import {Api} from "../../../api";
import {AlbumArt} from "../../album-art/album-art";
import {LoadingSpinner} from "../../loading-spinner/loading-spinner";
import {toast} from "react-toastify";
import {
	initializeYoutubeApiIfNeeded,
	YoutubeApiVideo,
	YoutubeVideoState
} from "../../../services/youtube-api-client";

const spotifyIdToYoutubeUrl = {};

export default function SpotifySearch() {
	const [spotifyTracks, setSpotifyTracks] = useState([]);
	const [apiInitialized, setApiInitialized] = useState(false);
	const [errorEncountered, setErrorEncountered] = useState(false);
	const [youtubeApiTimedOut, setYoutubeApiTimedOut] = useState(false);
	const [loading, setLoading] = useState(false);
	const [activeVideoSourceId, setActiveVideoSourceId] = useState(null);
	const [activeVideo, setActiveVideo] = useState(null);
	const [activeVideoState, setActiveVideoState] = useState(YoutubeVideoState.UNSTARTED);
	const [pendingDownloads, setPendingDownloads] = useState({});

	const [player, setPlayer] = useState(null);

	const musicFilterContext = useContext(MusicFilterContext);

	useEffect(() => {
		initializeYoutubeApiIfNeeded(
			success => {
				if (!apiInitialized) {
					setApiInitialized(true)
				}

				if (!success && !youtubeApiTimedOut) {
					setYoutubeApiTimedOut(true)
				}
			}
		);
	});

	useEffect(() => {
		if (musicFilterContext.searchTerm.length > 0) {
			setLoading(true);
			const term = encodeURIComponent(musicFilterContext.searchTerm);
			Api.get('search/spotify/artist/' + term).then(res => {
				setSpotifyTracks(res.items);
				setErrorEncountered(false);
			}).catch(err => {
				console.error(err);
				toast.error("An error was encountered searching Spotify");
				setErrorEncountered(true);
			}).finally(() => {
				setLoading(false);
			});
		}
	}, [musicFilterContext.searchTerm]);

	const getDisplayedText = () => {
		if (errorEncountered) {
			return <h3 className="text-center">Oh no! Something went wrong :(</h3>
		} else if (spotifyTracks.length === 0) {
			if (musicFilterContext.searchTerm.length === 0) {
				return <h3 className="text-center">Use the search bar to search for an artist on Spotify!</h3>
			} else if (!loading) {
				return <h3 className="text-center">No results found. Check that you searched for an artist's name</h3>
			}
		}

		return null;
	};

	const getPlayButtonClasses = spotifyTrack => {
		if (spotifyTrack.sourceId !== activeVideoSourceId || activeVideoState === YoutubeVideoState.PAUSED) {
			return 'fa-play';
		}

		if (activeVideoState === YoutubeVideoState.BUFFERING) {
			return 'fa-circle-notch animation-spin'
		} else {
			return 'fa-pause';
		}
	};

	const toggleTrackPlay = spotifyTrack => {
		if (spotifyTrack.sourceId === activeVideoSourceId) {
			if (activeVideoState === YoutubeVideoState.PAUSED) {
				player.playVideo();
				setActiveVideoState(YoutubeVideoState.PLAYING);
			} else {
				player.pauseVideo();
				setActiveVideoState(YoutubeVideoState.PAUSED);
			}
		} else {
			setActiveVideoSourceId(spotifyTrack.sourceId);
			setActiveVideoState(YoutubeVideoState.BUFFERING);

			const artist = encodeURIComponent(spotifyTrack.artist);
			const trackName = encodeURIComponent(spotifyTrack.name);

			Api.get(`search/youtube/artist/${artist}/track-name/${trackName}/length/${spotifyTrack.length}`).then(res => {
				spotifyIdToYoutubeUrl[spotifyTrack.sourceId] = res.videoUrl;
				if (res && res.id) {
					setActiveVideo(res);
				} else {
					toast.error('Failed to find a song to play!');
					setActiveVideo(null);
				}
			});
		}
	};

	const onPlayerReady = player => {
		setPlayer(player);
		player.playVideo();
	};

	const onStateChange = newState => {
		if (newState === YoutubeVideoState.PLAYING) {
			setActiveVideoState(newState)
		}
	};

	const setSourceLoading = (sourceId, isLoading) => {
		const newDownloads = Object.assign({}, pendingDownloads);
		newDownloads[sourceId] = isLoading;
		setPendingDownloads(newDownloads);
	};

	const importTrack = spotifyTrack => {
		setSourceLoading(spotifyTrack.sourceId, true);

		if (spotifyIdToYoutubeUrl[spotifyTrack.sourceId] !== undefined) {
			downloadFromYoutube(spotifyIdToYoutubeUrl[spotifyTrack.sourceId], spotifyTrack)
		} else {
			const term = encodeURIComponent(spotifyTrack.artist + ' ' + spotifyTrack.name);
			Api.get(`search/youtube/artist/${spotifyTrack.artist}/track-name/${spotifyTrack.name}/length/${spotifyTrack.length}`).then(res => {
				if (!res || !res.videoUrl) {
					toast.error(`Failed to find a video to download for ${spotifyTrack.name}`);
					setSourceLoading(spotifyTrack.sourceId, false);
				} else {
					downloadFromYoutube(res.videoUrl, spotifyTrack);
				}
			}).catch(error => {
				console.error(error);
				toast.error(`The download of ${spotifyTrack.name} failed`);

				setSourceLoading(spotifyTrack.sourceId, false);
			});
		}
	};

	const downloadFromYoutube = (url, spotifyTrack) => {
		const params = {
			url,
			name: spotifyTrack.name,
			artist: spotifyTrack.artist,
			album: spotifyTrack.album,
			releaseYear: spotifyTrack.releaseYear,
			trackNumber: spotifyTrack.trackNumber,
			artUrl: spotifyTrack.albumArtLink
		};

		Api.post('track/youtube-dl', params).then(() => {
			toast.success(`${spotifyTrack.name} downloaded successfully`);
		}).catch(error => {
			console.error(error);
			toast.error(`The download of ${spotifyTrack.name} failed`);
		}).finally(() => {
			setSourceLoading(spotifyTrack.sourceId, false);
		});
	};

	const getPlayerIconElement = track => {
		if (youtubeApiTimedOut) {
			return <i className="fas fa-ban" title="The song player could not be loaded. Imports are still possible"/>
		} else if (apiInitialized) {
			return <i className={`fas ${getPlayButtonClasses(track)}`} onClick={() => toggleTrackPlay(track)}/>
		} else {
			return <i className="fas fa-circle-notch animation-spin"/>
		}
	};

	return (
		<div id="spotify-search">
			<LoadingSpinner visible={loading}/>
			{ getDisplayedText() }

			{ apiInitialized && activeVideo !== null ? <YoutubeApiVideo
				videoId={activeVideo.id}
				embedUrl={activeVideo.embedUrl}
				onPlayerReady={onPlayerReady}
				onStateChange={onStateChange}
			/> : null }

			{ spotifyTracks.length > 0 ?
				<table className="song-table full-width">
					<thead>
					<tr>
						<th className="playback-button-column"/>
						<th className="min-width-column"/>
						<th>Name</th>
						<th>Artist</th>
						<th>Album</th>
						<th className="min-width-column">Year</th>
						<th className="import-column"/>
					</tr>
					</thead>
					<tbody>
					{ spotifyTracks.map(track =>
						<tr key={track.sourceId}>
							<td className="text-center">
								{ getPlayerIconElement(track) }
							</td>
							<td>
								<AlbumArt artLink={track.albumArtLink}/>
							</td>
							<td>{track.name}</td>
							<td>{track.artist}</td>
							<td>{track.album}</td>
							<td>{track.releaseYear}</td>
							<td className="text-center">
								{
									pendingDownloads[track.sourceId] === true
										? <span>Importing ...</span>
										: <button onClick={() => importTrack(track)}>Import</button>
								}
							</td>
						</tr>
					)}
					</tbody>
				</table> : null
			}
		</div>
	)
}
