export function reducer(state, {type, payload, error}) {
	switch (type) {
		case 'logIn':
			return {...state, firstLoad: true}
		case 'logOut':
			return {...initialState}
		case 'initStart':
			return {...state, firstLoad: false, initializing: true}
		case 'initDone':
			return {...state, initializing: false, ...payload}
		case 'initFail':
			return {...state, initializing: false, initError: error.message}
		case 'setNowPlaying':
			return {...state, nowPlaying: payload}
		case 'setCurrentTrack':
			return {...state, currentTrackData: payload.data, currentTrackIndex: payload.index}
		case 'updateCurrentTrack':
			const currentTrackData = state.currentTrackData
			Object.assign(currentTrackData, payload)
			return {...state, currentTrackData}
		default:
			return state
	}
}

export const unknownAlbumArt = './images/unknown-art.jpg'

export const initialState = {
	// init status
	firstLoad: true,
	initializing: false,
	initError: null,
	// dynamic information
	currentUser: null,
	nowPlaying: [],
	currentTrackData: {
		imgSrc: unknownAlbumArt
	},
	currentTrackIndex: -1,
	// data from server
	users: [],
	permissions: [],
	device: [],
	tracks: [],
	playlists: [],
	playlistTrackMap: {},
	reviewQueues: []
}