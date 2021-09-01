import React, {createContext, createRef, useCallback, useEffect, useReducer, useState} from 'react'
import {DeviceType, getDeviceIdentifier, isSafari} from '../util'
import {Api} from '../util/api'
import {addCookie, deleteCookie, getCookieValue} from '../util/cookie'
import {loadCurrentSettings} from '../util/table-settings'
import {getBoolean, getNumber, setBoolean, setNumber} from '../util/local-storage'
import {initialState as mainInitialState, reducer as mainReducer, unknownAlbumArt} from './mainReducer'
import {initialState as settingsInitialState, reducer as settingsReducer} from './settingsReducer'


const GorillaGrooveContext = createContext()

const GorillaGrooveProvider = ({children}) => {
	const [state, dispatch] = useReducer(mainReducer, mainInitialState)
	const [settingsState, settingsDispatch] = useReducer(settingsReducer, settingsInitialState)

	const [isPlaying, setIsPlaying] = useState(false)
	const [currentPlayTime, setCurrentPlayTime] = useState(0)
	const [totalTimeListened, setTotalTimeListened] = useState(0)

	const audioElementRef = createRef()  // it's a surprise tool that will help us later

	// the song has changed!!
	useEffect(() => {
		if (!!audioElementRef.current &&
			!!state.currentTrackData.src &&
			state.currentTrackData.src !== audioElementRef.current.src) {
			audioElementRef.current.src = state.currentTrackData.src
			audioElementRef.current.volume = settingsState.volume
		}
	}, [state.currentTrackData, state.currentTrackIndex])

	// we're playing, we're pausing
	useEffect(() => {
		if (!!audioElementRef.current && !!state.currentTrackData.src) {
			isPlaying ? audioElementRef.current.play() : audioElementRef.current.pause()
		}
	}, [isPlaying, state.currentTrackData.src])

	// just a manual play time update
	useEffect(() => {
		if (!!audioElementRef.current && currentPlayTime !== audioElementRef.current.currentTime) {
			audioElementRef.current.currentTime = currentPlayTime
		}
	}, [currentPlayTime])

	// we're listening, so listen time incremented
	useEffect(async () => {
		// do we need to tell the server about anything??? let's find out
		const timeTarget = state.currentTrackData.length * .6
		if (totalTimeListened > timeTarget && !state.currentTrackData.listenedTo) {
			// mark this immediately so we don't keep triggering this effect
			state.currentTrackData.listenedTo = true

			// TODO: add the retry

			await Api.post(
				'track/mark-listened',
				{
					trackId: state.currentTrackData.id,
					deviceId: getDeviceIdentifier(),
					timeListenedAt: (
						new Date()
					).toISOString(),
					ianaTimezone: Intl.DateTimeFormat().resolvedOptions().timeZone
				}
			)
			dispatch({
				type: 'updateCurrentTrack',
				payload: {playCount: state.currentTrackData.playCount + 1,
					lastPlayed: (new Date()).toISOString()
				}
			})
		}
	}, [totalTimeListened])

	const handlePlayTrack = useCallback(async (track, trackIndex) => {
		const {songLink, albumArtLink} = await Api.get(
			`file/link/${track.id}`,
			{audioFormat: isSafari() ? 'MP3' : 'OGG'}
		)
		Object.assign(track, {
			src: songLink,
			imgSrc: albumArtLink || unknownAlbumArt,
			listenedTo: false
		})
		dispatch({
			type: 'setCurrentTrack', payload: {
				data: track,
				index: trackIndex
			}
		})
		setIsPlaying(true)
		setCurrentPlayTime(0)
		setTotalTimeListened(0)
	}, [])

	const handleLogIn = async (email, password) => {
		const params = {
			email: email,
			password: password,
			deviceId: getDeviceIdentifier(),
			version: __VERSION__,
			deviceType: DeviceType.WEB
		}
		try {
			const {token, email} = await Api.post('authentication/login', params)
			const ninetyDays = 7776000
			addCookie('cookieToken', token, ninetyDays)
			addCookie('loggedInEmail', email, ninetyDays)
			dispatch({type: 'logIn'})
			return true
		}
		catch (error) {
			// TODO: make errors visible
			console.error(error)
		}
	}

	const handleLogOut = async () => {
		try {
			await Api.post('authentication/logout')
		}
		catch (error) {
			// TODO: does this error need to be visible?
			console.error(error)
		}
		finally {
			deleteCookie('cookieToken')
			deleteCookie('loggedInEmail')
		}
	}

	const handleInitFail = (e) => {
		console.error(e)
		dispatch({type: 'initFail', error: e})
	}

	const handleInit = async () => {
		// this is where we will get all the data to make the app go.
		dispatch({type: 'initStart'})

		// 1. load user data
		let currentUser, userResponse, permissionsResponse, deviceResponse

		try {
			//   a. all user data
			userResponse = await Api.get('user', {showAll: true})
			console.log('USERS', userResponse)
			// find the correct user in the response
			const loggedInEmail = getCookieValue('loggedInEmail')
			if (!loggedInEmail) {
				dispatch({type: actionTypes.initFail, error: 'Not logged in'})
				return
			}
			const currentUserIndex = userResponse.findIndex(({email}) => email.toLowerCase() ===
				loggedInEmail.toLowerCase())

			if (currentUserIndex >= 0) {
				currentUser = userResponse.splice(currentUserIndex, 1)[0]
			}
			else {
				dispatch({type: actionTypes.initFail, error: 'user with email ${loggedInEmail} not found in response'})
				return
			}

			//   b. permissions
			permissionsResponse = await Api.get('user/permissions')
			console.log('PERMISSIONS', permissionsResponse)
			//   c. device data
			deviceResponse = await Api.get('device/active?excluding-device=' + getDeviceIdentifier())
			console.log('DEVICE', deviceResponse)
		}
		catch (e) {
			return handleInitFail(e)
		}


		// 2. load user's music data
		let trackResponse, playlistResponse, playlistTrackMap, reviewQueueResponse

		try {
			//   a. library
			trackResponse = await Api.get('track/all')
			console.log('TRACKS', trackResponse)
			//   b. playlists
			playlistResponse = await Api.get('playlist')
			console.log('PLAYLISTS', playlistResponse)
			//   c. playlist mappings
			const playlistTrackMapResponse = await Api.get('playlist/track/mapping')
			console.log('PLAYLIST TRACK MAP', playlistTrackMapResponse)
			// this is a map of playlistId -> array of track ids in order
			playlistTrackMap = new Map()
			playlistTrackMapResponse.items.forEach(({playlistId, trackId, sortOrder}) => {
				if (!playlistTrackMap.has(playlistId)) {
					playlistTrackMap.set(playlistId, [])
				}
				const theMap = playlistTrackMap.get(playlistId)
				// we're going to assume that the server is giving us back reasonable sort order here
				theMap[sortOrder] = trackId
			})

			//   d. review queues
			reviewQueueResponse = await Api.get('review-queue')
			console.log('REVIEW QUEUES', reviewQueueResponse)
		}
		catch (e) {
			return handleInitFail(e)
		}

		// 3. load up local settings that don't require api calls
		//   a. table column settings
		const columnPreferences = loadCurrentSettings()
		//	 b. volume setting
		const volume = getNumber('volume', 1)
		//	 c. repeat setting
		const repeat = getBoolean('repeatSongs', false)

		settingsDispatch({
			type: 'init',
			payload: {
				columnPreferences,
				volume,
				repeat
			}
		})

		dispatch({
			type: 'initDone',
			payload: {
				currentUser,
				users: userResponse,
				permissions: permissionsResponse,
				device: deviceResponse,
				tracks: trackResponse.items,
				playlists: playlistResponse,
				playlistTrackMap,
				reviewQueues: reviewQueueResponse
			}
		})

	}

	const getTracks = (listId) => {
		if (listId === 0) {
			return state.tracks
		}
		else {
			// if the playlist isn't in the map, that means that its empty.
			if (!state.playlistTrackMap.has(listId)) {
				return []
			}
			// ok we need to look this up in the playlist map and return only the correct tracks
			const theMap = state.playlistTrackMap.get(listId)
			const theTracks = state.tracks.filter(track => theMap.includes(track.id))
			return theMap.map(trackId => theTracks.find(track => track.id === trackId))
		}
	}

	const handlePlayNext = () => {
		const nextIndex = state.currentTrackIndex + 1
		if (nextIndex < state.nowPlaying.length) {
			handlePlayTrack(state.nowPlaying[nextIndex], nextIndex)
		}
		else if (settingsState.repeat) {
			// loop back to the beginning
			handlePlayTrack(state.nowPlaying[0], 0)
		}
		else {
			// do a stop action on the current song, but keep it the current song.
			if (!!audioElementRef.current) {
				// TODO: i dont like having to set both of these all the time
				setIsPlaying(false)
				audioElementRef.current.pause()
				audioElementRef.current.currentTime = 0
			}
		}
	}

	return (
		<GorillaGrooveContext.Provider value={{
			logIn: handleLogIn,
			logOut: handleLogOut,
			init: handleInit,
			handleSetVolume: (newVolume) => {
				setNumber('volume', newVolume)
				if (!!audioElementRef.current) {
					audioElementRef.current.volume = newVolume
				}
				settingsDispatch({type: 'setVolume', payload: newVolume})
			},
			handleToggleRepeat: () => {
				setBoolean('repeatSongs', !settingsState.repeat)
				settingsDispatch({type: 'setRepeat', payload: !settingsState.repeat})
			},
			currentPlayTime,
			getTracks,
			setNowPlayingToList: (listId) => {
				dispatch({type: 'setNowPlaying', payload: getTracks(listId)})
			},
			playTrack: (track, index) => {
				handlePlayTrack(track, index)
			},
			handlePlayNext,
			handlePlayPrev: () => {
				const prevIndex = state.currentTrackIndex - 1
				if (prevIndex >= 0) {
					handlePlayTrack(state.nowPlaying[prevIndex], prevIndex)
				}
				else if (settingsState.repeat) {
					// loop back to the end
					const lastIndex = state.nowPlaying.length - 1
					handlePlayTrack(state.nowPlaying[lastIndex], lastIndex)
				}
				else {
					// start the current song over
					if (!!audioElementRef.current) {
						audioElementRef.current.currentTime = 0
					}
				}
			},
			handlePlayMusic: () => {
				if (!!state.currentTrackData.id && !!audioElementRef.current) {
					setIsPlaying(true)
					audioElementRef.current.play()
				}
			},
			handlePauseMusic: () => {
				if (!!audioElementRef.current) {
					setIsPlaying(false)
					audioElementRef.current.pause()
				}
			},
			handleSeekMusic: (newTime) => {
				if (!!audioElementRef.current) {
					audioElementRef.current.currentTime = newTime
				}
			},
			isPlaying,
			state: {
				...state,
				...settingsState
			}
		}}>
			{children}
			<audio hidden
				   ref={audioElementRef}
				   onTimeUpdate={() => {
					   // we need to compare this new time with the currently saved time, it dictates our actions here
					   const newTime = audioElementRef.current.currentTime

					   // ok update the time
					   setCurrentPlayTime(newTime)

					   // and update the total listened time, maybe
					   const timeElapsed = newTime - currentPlayTime
					   if (timeElapsed < 0 || timeElapsed > 1) {
						   // If the time elapsed went negative, or had a large leap forward (more than 1 second), then
						   // it means that someone manually altered the song's progress. Do no other checks or updates
						   return
					   }
					   setTotalTimeListened(totalTimeListened + timeElapsed)
				   }}
				   onEnded={handlePlayNext}
			>
				Your browser is ancient. Be less ancient.
			</audio>
		</GorillaGrooveContext.Provider>
	)
}

export {GorillaGrooveContext, GorillaGrooveProvider}