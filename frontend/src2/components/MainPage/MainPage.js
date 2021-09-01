import React, {useContext, useEffect} from 'react'
import {MemoryRouter, Route, Switch, withRouter} from 'react-router-dom'
import {GorillaGrooveContext} from '../../context/GorillaGrooveContext'
import Logo from '../Logo'
import Footer from './Footer'
import SideNavLink from './SideNavLink'
import TrackList from './TrackList/TrackList'


const MainPage = ({history}) => {
	const app = useContext(GorillaGrooveContext)

	useEffect(() => {
		if (app.state.firstLoad && !app.state.initializing) {
			app.init()
		}
	}, [app.state.firstLoad, app.state.initializing, app.init])

	if (app.state.firstLoad || app.state.initializing) {
		return (
			<div className={'init'}>
				<img src={'../../images/logo.png'} alt={'logo'} />
				<p>
					Initializing Gorillas...
				</p>
			</div>
		)
	}

	const handleLogOut = async () => {
		await app.logOut()
		history.push('/login')
	}

	if (app.state.initError) {
		handleLogOut()
	}


	return (
		<div className={'main'}>
			<div>
				<MemoryRouter initialEntries={['/list/0']}>
					<nav>
						<Logo />
						<p>Welcome, {app.state.currentUser.username}</p>
						<SideNavLink to={'/list/0'}>My Library</SideNavLink>
						<SideNavLink to={'/now-playing'}>Now Playing</SideNavLink>
						<div>
							<strong>Users:</strong>
							{app.state.users.map(user => (
								<div className={'navigable-item'}
									 key={user.id}>
									<a href={'#'}>
										{user.username}
									</a>
								</div>
							))}
						</div>
						<div>
							<strong>Playlists</strong>
							{app.state.playlists.map(playlist => (
									<SideNavLink key={playlist.id} to={`/list/${playlist.id}`}>
										{playlist.name}
									</SideNavLink>
								)
							)}
						</div>
						<button className={'link'} onClick={handleLogOut}>Log out</button>
					</nav>
					<div className={'panel'}>
						<Switch>
							<Route path={'/list/:listId'} exact={true}>
								<TrackList getTracks={(listId) => app.getTracks(listId)}
										   preferences={app.state.columnPreferences}
										   handlePlaySong={(track, index, listId) => {
											   app.setNowPlayingToList(listId)
											   // must still pass index, could be duplicated
											   app.playTrack(track, index)
										   }}
										   currentTrack={app.state.currentTrackData}
										   currentTrackIndex={app.state.currentTrackIndex}
								/>
							</Route>
							<Route path={'/now-playing'}>
								<TrackList getTracks={() => app.state.nowPlaying}
										   preferences={app.state.columnPreferences}
										   handlePlaySong={(track, index) => {
											   // must still pass index, could be duplicated
											   app.playTrack(track, index)
										   }}
										   currentTrack={app.state.currentTrackData}
										   currentTrackIndex={app.state.currentTrackIndex}
								/>
							</Route>
						</Switch>
					</div>
				</MemoryRouter>
			</div>
			<Footer currentTrack={app.state.currentTrackData}
					play={app.handlePlayMusic}
					pause={app.handlePauseMusic}
					seek={app.handleSeekMusic}
					isPlaying={app.isPlaying}
					currentPlayTime={app.currentPlayTime}
					volume={app.state.volume}
					setVolume={app.handleSetVolume}
					playNext={app.handlePlayNext}
					playPrev={app.handlePlayPrev}
					repeat={app.state.repeat}
					toggleRepeat={app.handleToggleRepeat}
			/>
		</div>
	)
}

export default withRouter(MainPage)