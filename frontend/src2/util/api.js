/* TODO: Lifted from original */

export class Api {

	static getBaseUrl() {
		const protocol = isLocalEnvironment() ? 'http' : 'https'
		return protocol + '://' + window.location.host
	}

	static getBaseApiUrl() {
		return this.getBaseUrl() + '/api/'
	}

	static getSocketUri() {
		const protocol = isLocalEnvironment() ? 'ws' : 'wss'
		return protocol + '://' + window.location.host + '/api/socket'
	}

	static get(url, params) {
		let urlParameters = Api.encodeUriParamsFromObject(params)

		return this.sendRequest('get', url + urlParameters)
	}

	static put(url, params) {
		return Api.sendRequest('put', url, params)
	}

	static post(url, params) {
		return Api.sendRequest('post', url, params)
	}

	static delete(url, params) {
		let urlParameters = Api.encodeUriParamsFromObject(params)

		return this.sendRequest('delete', url + urlParameters)
	}

	static sendRequest(requestType, url, params) {
		const headers = {'Content-Type': 'application/json'}

		const requestParams = {
			method: requestType,
			headers: new Headers(headers),
			credentials: 'include'
		}

		if (requestType !== 'get') {
			requestParams.body = JSON.stringify(params)
		}

		return fetch(this.getBaseApiUrl() + url, requestParams).then(res => {
			// The fetch API treats bad response codes like 4xx or 5xx as falling into the then() block
			// I don't like this behavior, since there was an issue. Throw an error so they fall into catch()
			if (!res.ok) {
				return res.text().then(text => {
					if (!text) {
						throw {error: res.statusText, status: res.status}
					}

					let json
					try {
						json = JSON.parse(text)
					}
					catch (e) {
						throw {error: text, status: res.status}
					}

					throw json
				})
			}

			// There isn't always a response body for POSTs, and calling res.json() will create a parse error
			// in the console, even within a try / catch. Really this is probably an issue with the server
			// and it probably shouldn't return empty responses. But that sounds more difficult to tackle
			return res.text().then(text => {
				return text ? JSON.parse(text) : {}
			})
		})
	};

	static download(url, params) {
		let urlParameters = Api.encodeUriParamsFromObject(params)

		const fullUrl = this.getBaseApiUrl() + url + urlParameters

		const a = document.createElement('a')
		a.href = fullUrl
		a.setAttribute('download', 'true')
		a.click()
	}

	static upload(httpMethod, url, params, progressCallback) {
		const fullUrl = this.getBaseApiUrl() + url

		return new Promise((resolve, reject) => {
			let formData = new FormData()

			Object.keys(params).forEach(paramKey => {
				formData.append(paramKey, params[paramKey])
			})

			let xhr = new XMLHttpRequest()
			xhr.open(httpMethod, fullUrl)
			xhr.withCredentials = true
			xhr.upload.addEventListener('progress', progressCallback, false)
			xhr.onload = function () {
				if (this.status >= 200 && this.status < 300) {
					resolve(xhr.response)
				}
				else {
					reject({
						status: this.status,
						statusText: xhr.statusText
					})
				}
			}
			xhr.onerror = function () {
				reject({
					status: this.status,
					statusText: xhr.statusText
				})
			}
			xhr.send(formData)
		})
	}

	static encodeUriParamsFromObject(params) {
		if (!params || Object.keys(params).length === 0) {
			return ''
		}

		let queryString = Object.keys(params).map(key => {
			// Spring wants multiple sort terms passed in like
			// sort=title,asc&sort=album,asc&sort=artist,asc
			// so repeat the sort term for each item, if we are dealing with the sort term
			if (key === 'sort') {
				return params[key].map(sortItem => {
					return `sort=${sortItem}`
				}).join('&')
			}
			else {
				let encodedKey = encodeURIComponent(key)
				let value = params[key]
				let encodedValue
				if (Array.isArray(value)) {
					encodedValue = value.map(it => encodeURIComponent(it)).join(',')
				}
				else {
					encodedValue = encodeURIComponent(params[key])
				}
				return `${encodedKey}=${encodedValue}`
			}
		}).join('&')

		return '?' + queryString
	}
}

function isLocalEnvironment() {
	// We only use a port in the UI when doing local dev
	return window.location.host.includes(':')
}
