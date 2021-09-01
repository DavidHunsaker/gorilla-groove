export function reducer(state, {type, payload, error}) {
	switch (type) {
		case 'init':
			return {...state, ...payload}
		case 'setVolume':
			return {...state, volume: payload}
		case 'setRepeat':
			return {...state, repeat: payload}
		default:
			return state
	}
}

export const initialState = {
	columnPreferences: {},
	volume: 100,
	repeat: false
}