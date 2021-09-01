import React, {useState} from 'react'
import MusicControls from '../MusicControls'
import Modal from '../Modal'


const Footer = ({currentTrack, play, pause, seek, isPlaying, currentPlayTime, volume, setVolume, playNext, playPrev, repeat, toggleRepeat}) => {
	const [isModalOpen, setIsModalOpen] = useState(false)

	const bringUpTheBigGuy = () => {
		setIsModalOpen(true)
	}

	const bringDownTheBigGuy = () => {
		setIsModalOpen(false)
	}

	return (
		<div className={'footer'}>
			<div className={'album-art'} style={{backgroundImage: `url(${currentTrack.imgSrc})`}}
				 onClick={bringUpTheBigGuy}>
			</div>
			<MusicControls currentTrack={currentTrack}
						   handlePlay={play}
						   handlePause={pause}
						   handleSeek={seek}
						   handlePlayNext={playNext}
						   handlePlayPrev={playPrev}
						   isPlaying={isPlaying}
						   currentPlayTime={currentPlayTime}
						   volume={volume}
						   handleSetVolume={setVolume}
						   repeatOn={repeat}
						   handleToggleRepeat={toggleRepeat}
			/>
			<Modal isOpen={isModalOpen} onClose={bringDownTheBigGuy}>
				<img className={'modal-image'} src={currentTrack.imgSrc} alt={'track data'} />
			</Modal>
		</div>
	)
}

export default Footer