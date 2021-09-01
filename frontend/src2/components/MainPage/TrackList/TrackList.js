import React, {createRef} from 'react'
import {FontAwesomeIcon} from '@fortawesome/react-fontawesome'
import {faQuestionCircle} from '@fortawesome/free-regular-svg-icons'

import {displayKeyToTrackKey, getAllPossibleDisplayColumns} from '../../../util/table-settings'
import {faSearch} from '@fortawesome/free-solid-svg-icons'
import {useParams} from 'react-router-dom'


const TrackList = ({getTracks, preferences, handlePlaySong, currentTrack, currentTrackIndex}) => {
	const {listId} = useParams()
	const tracks = getTracks(Number.parseInt(listId))

	// preferences is a map of col key -> enabled bool
	const colsToShow = getAllPossibleDisplayColumns().filter(col => preferences[col].enabled)
		.sort((col1, col2) => preferences[col1].order - preferences[col2].order)

	const renderTracks = () => {
		if (tracks.length === 0) {
			return (
				<tr>
					<td className={'empty'} colSpan={colsToShow.length}>
						There's nothing here
					</td>
				</tr>
			)
		}
		return tracks.map((track, index) => {
			const isCurrent = !!currentTrack && (!!listId ? track.id === currentTrack.id : index === currentTrackIndex)
			return (
				<tr key={index}
					onDoubleClick={() => handlePlaySong(track, index, Number.parseInt(listId))}
					className={isCurrent ? 'current' : ''}
				>
					{colsToShow.map(col => (
						<td key={col}>
							{track[displayKeyToTrackKey(col)]}
						</td>
					))}
				</tr>
			)
		})
	}

	const renderSearchInput = () => {
		const inputRef = createRef()
		return (
			<div className={'input-group'}>
				<FontAwesomeIcon icon={faSearch} onClick={() => {
					// if the icon is clicked on, we want to focus the input, but if the ref didn't work, forget it.
					if (inputRef.hasOwnProperty('current')) {
						inputRef.current.focus()
					}
				}} />
				<input aria-label={'Search this list'} type={'search'} placeholder={'Search'} ref={inputRef} />
			</div>
		)
	}

	const renderHiddenToggle = () => {
		const hiddenHelpText = 'Show songs that have been hidden from the library.\n' +
			'This is not the same as private tracks, which are only visible to the owner.'
		return (
			<>
				<input type={'checkbox'} id={'show-hidden'} aria-describedby={'hidden-help'} />
				<label htmlFor={'show-hidden'}>
					Show hidden tracks
				</label>
				<FontAwesomeIcon icon={faQuestionCircle} className={'help'} title={hiddenHelpText} />
				<p id={'hidden-help'} hidden={true}>
					{hiddenHelpText}
				</p>
			</>
		)
	}

	return (
		<>
			<div className={'search-tools'}>
				{renderSearchInput()}
				<div className={'filter-group'}>
					<span>View by: </span>
					<button className={'link current'}>All</button>
					<button className={'link'}>Artist</button>
					<button className={'link'}>Album</button>
					<button className={'link'}>Vector</button>
					{renderHiddenToggle()}
				</div>
			</div>
			<div className={'list'}>
				<table>
					<thead>
					<tr>
						{colsToShow.map(col => <th key={col}>{col}</th>)}
					</tr>
					</thead>
					<tbody>
					{renderTracks()}
					</tbody>
				</table>
			</div>
		</>
	)
}

export default TrackList