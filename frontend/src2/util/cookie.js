/* TODO: lifted from original */

export function addCookie(name, value, expiration) {
	document.cookie = `${name}=${value};max-age=${expiration};path=/;SameSite=Strict`
}

export function deleteCookie(name) {
	document.cookie = '' + name +'=; Path=/; Expires=Thu, 01 Jan 1970 00:00:01 GMT;';
}

export function getCookieValue(name) {
	const allCookies = "; " + document.cookie;
	const parts = allCookies.split("; " + name + "=");
	if (parts.length === 2) {
		return parts
			.pop()
			.split(";")
			.shift();
	} else {
		return undefined;
	}
}
