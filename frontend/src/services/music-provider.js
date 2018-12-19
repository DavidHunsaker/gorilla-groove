import React from "react";
import {Api} from "../api";

export const MusicContext = React.createContext();

export class MusicProvider extends React.Component {
	constructor(props) {
		super(props);

		this.state = {
			viewedTracks: [],
			trackSortColumn: 'Artist',
			trackSortDir: 'asc',
			nowPlayingTracks: [],
			playedTrack: null,
			playedTrackIndex: null,
			lastLoadedUserId: null, // This won't work when playlists are a thing... Works for now though
			loadSongsForUser: (...args) => this.loadSongsForUser(...args),
			forceTrackUpdate: (...args) => this.forceTrackUpdate(...args),
			playFromTrackIndex: (...args) => this.playFromTrackIndex(...args),
			playTracks: (...args) => this.playTracks(...args),
			playTracksNext: (...args) => this.playTracksNext(...args),
			playTracksLast: (...args) => this.playTracksLast(...args),
			playNext: (...args) => this.playNext(...args),
			setHidden: (...args) => this.setHidden(...args)
		};

		this.trackKeyConversions = {
			'Name': 'name',
			'Artist': 'artist',
			'Album': 'album',
			'Length': 'length',
			'Year': 'releaseYear',
			'Play Count': 'playCount',
			'Bit Rate': 'bitRate',
			'Sample Rate': 'sampleRate',
			'Added': 'createdAt',
			'Last Played': 'lastPlayed',
		};
	}

	loadSongsForUser(userId, sortColumn, sortDir) {
		let params = {};

		// Default to the last loaded user if no user is present. If null, the backend uses the current user
		if (userId) {
			params.userId = userId;
			this.setState({ lastLoadedUserId: userId })
		} else if (this.state.lastLoadedUserId) {
			params.userId = this.state.lastLoadedUserId;
		}

		if (sortColumn && sortDir) {
			params.sort = `${this.trackKeyConversions[sortColumn]},${sortDir}`;

			this.setState({
				trackSortColumn: sortColumn,
				trackSortDir: sortDir
			});
		} else {
			params.sort = `${this.trackKeyConversions[this.state.trackSortColumn]},${this.state.trackSortDir}`
		}

		Api.get("track", params)
			.then((result) => {
				this.setState({ viewedTracks: result.content });
			}).catch((error) => {
			console.error(error)
		});
	}

	playFromTrackIndex(trackIndex, updateNowPlaying) {
		this.setState({ playedTrackIndex: trackIndex });

		if (updateNowPlaying) {
			this.setState({
				nowPlayingTracks: this.state.viewedTracks.slice(0),
				playedTrack: this.state.viewedTracks[trackIndex]
			})
		} else {
			this.setState({ playedTrack: this.state.nowPlayingTracks[trackIndex] });
		}
	}

	playTracks(tracks) {
		this.setState({
			nowPlayingTracks: tracks,
			playedTrack: tracks[0],
			playedTrackIndex: 0
		})
	}

	playTracksNext(tracks) {
		// Feels kind of dirty to mutate the original then pass it in as setState
		this.state.nowPlayingTracks.splice(this.state.playedTrackIndex + 1, 0, ...tracks);
		this.setState({ nowPlayingTracks: this.state.nowPlayingTracks });
	}

	playTracksLast(tracks) {
		this.state.nowPlayingTracks.splice(this.state.nowPlayingTracks.length, 0, ...tracks);
		this.setState({ nowPlayingTracks: this.state.nowPlayingTracks });
	}

	forceTrackUpdate() {
		this.setState({
			nowPlayingTracks: this.state.nowPlayingTracks,
			viewedTracks: this.state.viewedTracks
		});
	}

	playNext() {
		let newTrackIndex = this.state.playedTrackIndex + 1;
		this.setState({
			playedTrackIndex: newTrackIndex,
			playedTrack: this.state.nowPlayingTracks[newTrackIndex]
		})
	}

	setHidden(tracks, isHidden) {
		Api.post('track/set-hidden', {
			trackIds: tracks.map(track => track.id),
			isHidden: isHidden
		}).then(() => {
			tracks.forEach(track => track.hidden = isHidden);
			this.forceTrackUpdate();
		}).catch(error => {
			console.error(error);
		});
	}

	render() {
		return (
			<MusicContext.Provider value={this.state}>
				{this.props.children}
			</MusicContext.Provider>
		)
	}
}
