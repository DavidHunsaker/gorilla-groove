import React from 'react'
import ReactModal from 'react-modal'
import {FontAwesomeIcon} from '@fortawesome/react-fontawesome'
import {faTimes} from '@fortawesome/free-solid-svg-icons'


// Make sure to bind modal to your appElement (http://reactcommunity.org/react-modal/accessibility/)
ReactModal.setAppElement('#root')

const Modal = ({isOpen, onClose, children}) => {
	return (
		<ReactModal isOpen={isOpen}
					className={'modal'}
					onRequestClose={onClose}
					shouldCloseOverlayOnClick={true}
		>
			<button className={'modal-close icon'} onClick={onClose}>
				<FontAwesomeIcon icon={faTimes} />
			</button>
			{children}
		</ReactModal>
	)
}

export default Modal