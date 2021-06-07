import * as LocalStorage from './local-storage';

/* TODO: functions lifted from original */

// https://stackoverflow.com/a/2117523
export function uuidv4() {
	return ([1e7]+-1e3+-4e3+-8e3+-1e11).replace(/[018]/g, c =>
		(c ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> c / 4).toString(16)
	);
}


export function getDeviceIdentifier() {
	const deviceId = LocalStorage.getString('deviceId');
	if (deviceId) {
		return deviceId;
	}

	const newId = uuidv4();
	LocalStorage.setString('deviceId', newId);

	return newId;
}


/**
 * Verifies basic information to determine client side if the user is logged in.
 * @returns {boolean}
 */
export function isLoggedIn() {
	return document.cookie.indexOf('cookieToken') !== -1;
}