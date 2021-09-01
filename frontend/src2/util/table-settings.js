import {getObject, setObject} from './local-storage'
import {arrayDifference} from './'


const displayKeyToTrackKeyMap = {
	'Name': 'name',
	'Artist': 'artist',
	'Featuring': 'featuring',
	'Album': 'album',
	'Track #': 'trackNumber',
	'Length': 'length',
	'Year': 'releaseYear',
	'Genre': 'genre',
	'Play Count': 'playCount',
	'Added': 'addedToLibrary',
	'Last Played': 'lastPlayed',
	'Note': 'note'
}

export function displayKeyToTrackKey(displayKey) {
	return displayKeyToTrackKeyMap[displayKey]
}

export function getAllPossibleDisplayColumns() {
	return Object.keys(displayKeyToTrackKeyMap)
}

export function loadCurrentSettings() {
	const columnPreferences = {}

	const savedPreferences = getObject('columnPreferences')
	const columnOptions = getAllPossibleDisplayColumns()

	if (savedPreferences) {
		// STEP ONE, check if there's a difference between what's SAVED and what's AVAILABLE. if there is a
		// difference, we will need to save the settings again so this doesn't happen next load.
		const savedColumns = savedPreferences.map(col => col.name)
		const newCols = arrayDifference(columnOptions, savedColumns)
		const oldCols = arrayDifference(savedColumns, columnOptions)
		// STEP TWO: filter settings array to only valid settings, loop over to build object. then add on new
		// columns to the end
		let lastIndex = 0
		savedPreferences.filter(col => columnOptions.includes(col.name)).forEach((col, index) => {
			columnPreferences[col.name] = {enabled: col.enabled, order: index}
			lastIndex++
		})
		newCols.forEach((col, index) => columnPreferences[col] = {enabled: true, order: index + lastIndex})
		// STEP THREE: if there was a difference, parse the new settings object to the save format and save it.
		if (newCols.length > 0 || oldCols.length > 0) {
			setObject('columnPreferences',
				Object.entries(columnPreferences).sort(([key1, value1], [key2, value2]) => value1.order -
					value2.order).map(([key, {enabled}]) => (
					{name: key, enabled}
				))
			)
		}
	}
	else {
		columnOptions.forEach((trackColumnName, index) => (
			columnPreferences[trackColumnName] = {enabled: true, order: index}
		))
	}
	return columnPreferences
}