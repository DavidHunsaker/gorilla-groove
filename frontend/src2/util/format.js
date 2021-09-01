
export function formatTimeFromSeconds(lotsOfSeconds) {
	lotsOfSeconds = parseInt(lotsOfSeconds);

	if (isNaN(lotsOfSeconds)) {
		return '0:00'
	}

	const minutes = parseInt(lotsOfSeconds / 60);
	const seconds = lotsOfSeconds % 60;
	const paddedSeconds = (seconds < 10 ? '0' : '') + seconds;
	return `${minutes}:${paddedSeconds}`;
}