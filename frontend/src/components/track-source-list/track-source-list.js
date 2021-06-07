import React, {useContext, useEffect, useState} from 'react';
import TreeView from 'react-treeview'
import {MusicContext} from "../../services/music-provider";
import {CenterView, TrackView} from "../../enums/site-views";
import {EditableDiv} from "../editable-div/editable-div";
import AddPlaylistButton from "../add-playlist/add-playlist";
import {Modal} from "../modal/modal";
import {SocketContext} from "../../services/socket-provider";
import {UserContext} from "../../services/user-provider";
import {PlaylistContext} from "../../services/playlist-provider";
import {DeviceContext} from "../../services/device-provider";
import {ReviewQueueContext} from "../../services/review-queue-provider";
import ReviewQueueManagement from "../review-queue/review-queue-management/review-queue-management";
import {PlaybackContext} from "../../services/playback-provider";
import {DeviceType} from "../../enums/device-type";
import {PermissionType} from "../../enums/permission-type";

let pendingDeletePlaylist = {};

export default function TrackSourceList(props) {
	const [collapsedBookkeeping, setCollapsedBookkeeping] = useState([false, false, false]);
	const [editedId, setEditedId] = useState(null);
	const [modalOpen, setModalOpen] = useState(false);

	const userContext = useContext(UserContext);
	const musicContext = useContext(MusicContext);
	const playbackContext = useContext(PlaybackContext);
	const deviceContext = useContext(DeviceContext);
	const socketContext = useContext(SocketContext);
	const playlistContext = useContext(PlaylistContext);
	const reviewQueueContext = useContext(ReviewQueueContext);

	const reviewQueueSelected = props.centerView === CenterView.REVIEW_QUEUE && reviewQueueContext.viewedReviewSourceId === undefined
		? 'selected' : '';

	const dataSource = [
		{
			section: CenterView.REVIEW_QUEUE,
			heading: <div className={`flex-between ${reviewQueueSelected}`}>
				<span className="flex-grow hoverable">In Review
					<span className="small-text">
						{ reviewQueueContext.reviewQueueCount > 0 ? ` (${reviewQueueContext.reviewQueueCount})` : ''}
					</span>
				</span>
				<ReviewQueueManagement/>
			</div>,
			data: reviewQueueContext.reviewQueueSources.filter(it => it.trackCount > 0)
		},
		{
			section: TrackView.USER,
			heading: 'User Libraries',
			data: userContext.otherUsers
		}, {
			section: TrackView.PLAYLIST,
			heading: <span className="playlist-heading">Playlists <AddPlaylistButton/></span>,
			data: props.playlists
		}
	];

	const handleEditStop = event => {
		if (
			event.target.tagName !== 'INPUT'
			&& event.target.id !== editedId
			&& editedId !== null
		) {
			setEditedId(null);
		}
	};

	useEffect(() => {
		document.body.addEventListener('click', handleEditStop);

		return () => {
			document.body.removeEventListener('click', handleEditStop);
		}
	}, [editedId]);

	const handleParentNodeClick = (i, heading) => {
		if (heading === CenterView.REVIEW_QUEUE) {
			reviewQueueContext.setViewedSourceId(undefined);
			props.setCenterView(CenterView.REVIEW_QUEUE);
			return;
		}

		const [...bookkeeping] = collapsedBookkeeping;
		bookkeeping[i] = !bookkeeping[i];
		setCollapsedBookkeeping(bookkeeping);
	};

	const selectEntry = (section, entry, elementId) => {
		if (
			props.centerView === CenterView.TRACKS &&
			section === musicContext.trackView &&
			entry.id === musicContext.viewedEntityId
		) {
			setEditedId(elementId);
			return; // User selected the same thing as before
		}
		setEditedId(null);

		if (section === TrackView.USER) {
			props.setCenterView(CenterView.TRACKS);
			musicContext.loadSongsForUser(entry.id, {}, false);
		} else if (section === TrackView.PLAYLIST) {
			props.setCenterView(CenterView.TRACKS);
			musicContext.loadSongsForPlaylist(entry.id, {}, false);
		} else {
			reviewQueueContext.setViewedSourceId(entry.id);
			props.setCenterView(CenterView.REVIEW_QUEUE);
		}
	};

	const getNowPlayingElement = entry => {
		const devicesForUser = socketContext.nowListeningUsers[entry.id];
		if (!devicesForUser) {
			return null;
		}

		const listeningDevices = Object.values(devicesForUser);

		const playingDevices = listeningDevices.filter(device => device.isPlaying && device.trackData);

		if (playingDevices.length === 0) {
			return null;
		}

		const isMobile = playingDevices.every(device => {
			return device.deviceType === DeviceType.IPHONE || device.deviceType === DeviceType.ANDROID
		});

		const displayText = playingDevices.map(device => {
			let infoString;
			if (device.trackData.isPrivate) {
				infoString = 'This track is private';
			} else {
				infoString = `${device.trackData.name} - ${device.trackData.artist}`;
			}

			let inReviewString = device.trackData.inReview ? '\nIn Review' : '';

			return `${infoString}\nDevice: ${device.deviceName}${inReviewString}`;
		}).join('\n\n');

		return <span className="user-listening hoverable" title={displayText} onClick={e => {
			e.stopPropagation();
			// They could be playing from more than one device, but such an edge case I don't want to deal with it right now
			if (!playingDevices[0].trackData.isPrivate) {
				const id = playingDevices[0].trackData.id;
				musicContext.loadTrackById(id).then(() => {
					playbackContext.setProviderState({ isPlaying: true });
				});
			}
		}}>
			{ isMobile ? <i className="fas fa-mobile"/> : <i className="fas fa-music"/> }
		</span>
	};

	const isUserPartying = entry => {
		// If we can see any of the user's devices as active, we know we've been invited to a party
		const devicesForUser = deviceContext.otherDevices.filter(it => it.userId === entry.id);
		return devicesForUser.length > 0;
	};

	const librarySelected = musicContext.trackView === TrackView.LIBRARY && props.centerView === CenterView.TRACKS
		? 'selected' : '';
	const deviceManagementSelected = props.centerView === CenterView.REMOTE_DEVICES ? 'selected' : '';
	const globalSearchSelected = props.centerView === CenterView.GLOBAL_SEARCH ? 'selected' : '';
	const spotifySearchSelected = props.centerView === CenterView.SPOTIFY_SEARCH ? 'selected' : '';

	return (
		<div id="view-source-list">
			<div
				className={`large-option ${librarySelected} hoverable`}
				onClick={() => {
					props.setCenterView(CenterView.TRACKS);
					musicContext.loadSongsForUser();
				}}
			>
				<span className="my-library">My Library</span>
			</div>

			<div
				className={`secondary-option ${deviceManagementSelected} hoverable`}
				onClick={() => {
					props.setCenterView(CenterView.REMOTE_DEVICES);
				}}
			>
				<span>Remote Play</span>
			</div>

			<div
				className={`secondary-option ${spotifySearchSelected} hoverable`}
				onClick={() => {
					props.setCenterView(CenterView.SPOTIFY_SEARCH);
				}}
			>
				<span>Spotify</span>
			</div>

			<div
				className={`secondary-option ${globalSearchSelected} hoverable`}
				onClick={() => {
					props.setCenterView(CenterView.GLOBAL_SEARCH);
				}}
			>
				<span>YouTube</span>
			</div>

			{dataSource.map((node, i) => {
				const label =
					<div className="tree-node" onClick={() => handleParentNodeClick(i, node.section)}>
						{node.heading}
					</div>;
				return (
					<TreeView
						key={i}
						nodeLabel={label}
						collapsed={collapsedBookkeeping[i]}
						onClick={() => handleParentNodeClick(i)}
					>
						{node.data.map(entry => {
							let entrySelected = false;
							if (node.section === CenterView.REVIEW_QUEUE) {
								entrySelected = props.centerView === CenterView.REVIEW_QUEUE
									&& entry.id === reviewQueueContext.viewedReviewSourceId;
							} else {
								entrySelected = musicContext.trackView === node.section &&
								entry.id === musicContext.viewedEntityId &&
								props.centerView === CenterView.TRACKS;
							}

							const entryClass = entrySelected ? 'selected' : '';
							const partyClass = node.section === TrackView.USER && isUserPartying(entry) ? 'animation-rainbow' : '';

							let tooltip = '';
							if (partyClass) {
								tooltip = 'This user invited you to party';
							} else if (node.section === CenterView.REVIEW_QUEUE) {
								if (entry.trackCount > 0) {
									tooltip = entry.displayName + ` (${entry.trackCount})`;
								} else {
									tooltip = entry.displayName;
								}
							}

							const cellId = i + '-' + entry.id;

							let text = '';
							if (node.section === TrackView.USER) {
								text = entry.username;
							} else if (node.section === TrackView.PLAYLIST) {
								text = entry.name;
							} else if (node.section === CenterView.REVIEW_QUEUE) {
								text = <span>
									{ entry.displayName }
									<span className="small-text">
										{ entry.trackCount > 0 ? ` (${entry.trackCount})` : ''}
									</span>
								</span>
							}

							return (
								<div
									id={cellId}
									title={tooltip}
									className={`tree-child ${entryClass} ${partyClass}`}
									key={entry.id}
									onClick={(event) => {
										event.stopPropagation();
										selectEntry(node.section, entry, cellId)
									}}
								>
									<EditableDiv
										editable={editedId === cellId && node.section === TrackView.PLAYLIST}
										showTooltip={node.section !== CenterView.REVIEW_QUEUE}
										text={text}
										stopEdit={() => setEditedId(null)}
										updateHandler={newValue => playlistContext.renamePlaylist(entry, newValue)}
									/>
									{ node.section === TrackView.USER ? getNowPlayingElement(entry) : <React.Fragment/> }

									<div className="playlist-delete">
										{ node.section === TrackView.PLAYLIST
											? <i className="fas fa-times" onClick={e => {
												e.stopPropagation();
												pendingDeletePlaylist = entry;
												setModalOpen(true);
											}}/>
											: <i/>
										}
									</div>

								</div>
							)
						})}
					</TreeView>
				);
			})}
			<Modal
				isOpen={modalOpen}
				closeFunction={() => setModalOpen(false)}
			>
				<div id="playlist-delete-modal">
					<div>Are you sure you want to delete the playlist '{pendingDeletePlaylist.name}'?</div>
					<div className="flex-between confirm-modal-buttons">
						<button onClick={() => {
							playlistContext.deletePlaylist(pendingDeletePlaylist).then(() => {
								if (musicContext.trackView === TrackView.PLAYLIST
									&& musicContext.viewedEntityId === pendingDeletePlaylist.id) {
									musicContext.loadSongsForUser();
								}
								setModalOpen(false)
							})
						}}>You know I do</button>
						<button onClick={() => setModalOpen(false)}>No. Woops</button>
					</div>
				</div>
			</Modal>

		</div>
	);
}


