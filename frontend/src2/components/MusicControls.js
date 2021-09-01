import React, {useState} from 'react'
import {FontAwesomeIcon} from '@fortawesome/react-fontawesome'
import {
	faPause,
	faPlay,
	faRandom,
	faStepBackward,
	faStepForward,
	faSyncAlt,
	faVolumeDown,
	faVolumeMute,
	faVolumeOff,
	faVolumeUp
} from '@fortawesome/free-solid-svg-icons'
import {formatTimeFromSeconds} from '../util/format'


const MusicControls = ({
	currentTrack,
	handlePlay,
	handlePause,
	handleSeek,
	isPlaying,
	currentPlayTime,
	volume,
	handleSetVolume,
	handlePlayNext,
	handlePlayPrev,
	repeatOn,
	handleToggleRepeat
}) => {
	const [tempShuffleOn, setTempShuffleOn] = useState(false)
	const [isMuted, setIsMuted] = useState(false)

	const getVolumeIcon = () => {
		if (isMuted) {
			return faVolumeMute
		}
		if (volume < 10) {
			return faVolumeOff
		}
		else if (volume < 50) {
			return faVolumeDown
		}
		else {
			return faVolumeUp
		}
	}

	return (
		<div className={'music-control'}>
			<div className={'now-playing'}>
				{currentTrack.name} - {currentTrack.artist}
			</div>
			<div className={'button-box'}>
				<div>
					<button className={'icon'} aria-label={'Previous'} onClick={handlePlayPrev}>
						<FontAwesomeIcon icon={faStepBackward} size={'2x'} />
					</button>
					<button className={'icon'} aria-label={isPlaying ? 'Pause' : 'Play'}
							onClick={() => {
								if (isPlaying) {
									handlePause()
								}
								else {
									handlePlay()
								}
							}}>
						<FontAwesomeIcon icon={isPlaying ? faPause : faPlay} size={'3x'} />
					</button>
					<button className={'icon'} aria-label={'Next'} onClick={handlePlayNext}>
						<FontAwesomeIcon icon={faStepForward} size={'2x'} />
					</button>
				</div>
				<div>
					<button className={`icon ${tempShuffleOn ? 'active' : ''}`}
							aria-label={tempShuffleOn ? 'Turn Shuffle Off' : 'Turn Shuffle On'}
							onClick={() => setTempShuffleOn(!tempShuffleOn)}>
						<FontAwesomeIcon icon={faRandom} size={'2x'} />
					</button>
					<button className={`icon ${repeatOn ? 'active' : ''}`}
							aria-label={repeatOn ? 'Turn Repeat Off' : 'Turn Repeat On'}
							onClick={handleToggleRepeat}>
						<FontAwesomeIcon icon={faSyncAlt} size={'2x'} />
					</button>
				</div>
			</div>
			<div className={'seeker'}>
				<div>{formatTimeFromSeconds(currentPlayTime)} / {formatTimeFromSeconds(currentTrack.length)}</div>
				<progress aria-label={'Seek'}
						  max={'100'}
						  value={currentTrack.length > 0 ? (
							  currentPlayTime / currentTrack.length * 100
						  ) : 0}
						  onClick={(event) => {
							  if (!currentTrack.id) {
								  return
							  }
							  const percentage = (
								  event.nativeEvent.offsetX || 0
							  ) / event.currentTarget.offsetWidth
							  handleSeek(currentTrack.length * percentage)
						  }}
				/>
			</div>
			<div className={'volume'}>
				<button className={'icon volume-indicator'}>
					<FontAwesomeIcon icon={getVolumeIcon()}
									 size={'2x'}
									 onClick={() => setIsMuted(!isMuted)} />
				</button>
				<input aria-label={'Volume'}
					   type={'range'}
					   min={'0'}
					   max={'100'}
					   value={volume * 100}
					   step={'1'}
					   onChange={(event) => handleSetVolume(event.target.value / 100)}
				/>
			</div>
		</div>
	)
}

export default MusicControls