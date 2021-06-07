import React, {useEffect, useReducer} from 'react';
import {withRouter} from 'react-router-dom';
import {deleteCookie} from '../../../src/cookie';
import {Api} from '../../util/api';
import Logo from '../Logo';
import {getDeviceIdentifier} from '../../util';
import {getCookieValue} from '../../util/cookie';


const setUsersAction = 'setUsers';
const startInitAction = 'initStart';
const completeInitAction = 'initDone';
const setPlaylistsAction = 'setPlaylists';
const setTracksAction = 'setTracks';

const MainPage = ({history}) => {
	const reducer = (state, {type, payload, error}) => {
		switch (type) {
			case startInitAction:
				return {...state, initing: true};
			case completeInitAction:
				return {...state, initing: false};
			case setUsersAction:
				return {...state, ...payload};
			case setPlaylistsAction:
				return {...state, playlists: payload};
			case setTracksAction:
				return {...state, tracks: payload};
			default:
				return state;
		}
	};

	const [state, dispatch] = useReducer(reducer, {
		initing: true,
		currentUser: null,
		otherUsers: [],
		playlists: [],
		tracks: []

	});

	const handleLogOut = async (event) => {
		event.preventDefault();
		try {
			await Api.post('authentication/logout');
		} catch (error) {
			console.error(error);
		} finally {
			deleteCookie('cookieToken');
			deleteCookie('loggedInEmail');

			history.push('/login');
		}
	};

	const handleInit = async () => {
		// this is where we will get all the data.
		// 1. load user data
		//   a. all user data - if a request fails, redirect to login
		const userResponse = await Api.get('user', {showAll: false});
		console.log(userResponse);
		// find the correct user in the response
		const loggedInEmail = getCookieValue('loggedInEmail').toLowerCase();
		const currentUserIndex = userResponse.findIndex(({email}) => email.toLowerCase() === loggedInEmail);
		let currentUser;
		if (currentUserIndex >= 0) {
			currentUser = userResponse.splice(currentUserIndex, 1)[0];
			dispatch({type: setUsersAction, payload: {currentUser, otherUsers: userResponse}});
		}
		else {
			console.error(`user with email ${loggedInEmail} not found in response`);
			history.push('/login');
		}

		//   b. permissions
		const permissionsResponse = await Api.get('user/permissions');
		console.log(permissionsResponse);

		//   c. device data
		const deviceResponse = await Api.get('device/active?excluding-device=' + getDeviceIdentifier());
		console.log(deviceResponse);

		// 2. load user's music data
		//   a. library
		const trackResponse = await Api.get('track/all');
		console.log(trackResponse);
		dispatch({type: setTracksAction, payload: trackResponse.items});

		//   b. playlists
		const playlistResponse = await Api.get('playlist');
		console.log(playlistResponse);
		dispatch({type: setPlaylistsAction, payload: playlistResponse});

		//   c. playlist mappings
		const playlistTrackMapResponse = await Api.get('playlist/track/mapping');
		console.log(playlistTrackMapResponse);
		//   d. review queues
		const reviewQueueResponse = await Api.get('review-queue');
		console.log(reviewQueueResponse);

		dispatch({type: completeInitAction});
	};

	useEffect(() => {
		if (state.initing) {
			// show init message 1 sec + actual load time
			setTimeout(handleInit, 1000);
		}
	}, [state.initing]);

	if (state.initing) {
		return (
			<div className={'init'}>
				<img src={'../../images/logo.png'} alt={'logo'}/>
				<p>
					Initializing Gorillas...
				</p>
			</div>
		);
	}

	return (
		<div className={'main'}>
			<div>
				<div className={'nav'}>
					<Logo/>
					<p>Welcome, {state.currentUser.username}</p>
					<div>
						<strong>Users:</strong>
						{state.otherUsers.map(user => <div key={user.id}>{user.username}</div>)}
					</div>
					<div>
						<strong>Playlists</strong>
						{state.playlists.map(playlist => <div key={playlist.id}>{playlist.name}</div>)}
					</div>
					<button className={'link'} onClick={handleLogOut}>
						Log out
					</button>
				</div>
				<div className={'panel'}>
					<p>these are the tracks i got back for your library</p>
					<div className={'track-list'}>
						<table>
							<thead>
							<tr>
								<th>Name</th>
								<th>Artist</th>
								<th>Album</th>
								<th>Track #</th>
							</tr>
							</thead>
							<tbody>
							{state.tracks.map((track, index) => (
								<tr key={index}>
									<td>{track.name}</td>
									<td>{track.artist}</td>
									<td>{track.album}</td>
									<td>{track.trackNumber}</td>
								</tr>
							))}
							</tbody>
						</table>
					</div>
				</div>
			</div>
			<div className={'footer'}>
				<div className={'album-art'} style={{backgroundImage: `url(${'../../../images/unknown-art.jpg'})`}}>
				</div>
				<p>this is where play controls will be</p>
			</div>
		</div>
	);
};

export default withRouter(MainPage);