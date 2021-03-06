import React from 'react';
import {Api} from "../../api";
import {Modal} from "../modal/modal";
import {toast} from "react-toastify";
import {MusicContext} from "../../services/music-provider";

export class YoutubeDlButton extends React.Component {
	constructor(props) {
		super(props);

		this.state = {
			modalOpen: false,
			downloading: false,
			cropArtToSquare: true
		}
	}

	setModalOpen(isOpen) {
		this.setState({ modalOpen: isOpen })
	}

	submitDownloadForm(event) {
		event.preventDefault();
		event.nativeEvent.propagationStopped = true;

		const url = document.getElementById('song-url').value;
		const name = document.getElementById('song-name').value;
		const artist = document.getElementById('song-artist').value;
		const featuring = document.getElementById('song-featuring').value;
		const album = document.getElementById('song-album').value;
		const year = document.getElementById('song-year').value;
		const trackNumber = document.getElementById('song-track-number').value;
		const genre = document.getElementById('song-genre').value;

		if (url.includes("&list")) {
			toast.error("Playlist downloads are not supported");
			return;
		}

		const params = {
			url: url,
			cropArtToSquare: this.state.cropArtToSquare
		};

		if (name) {
			params.name = name;
		}
		if (artist) {
			params.artist = artist;
		}
		if (album) {
			params.album = album;
		}
		if (year) {
			params.releaseYear = year;
		}
		if (trackNumber) {
			params.trackNumber = trackNumber;
		}
		if (genre) {
			params.genre = genre;
		}
		if (featuring) {
			params.featuring = featuring;
		}

		this.setState({
			downloading: true,
			modalOpen: false
		});

		Api.post('track/youtube-dl', params).then(track => {
			this.context.addUploadToExistingLibraryView(track);
			toast.success(`YouTube song downloaded successfully`);
		}).catch(error => {
			console.error(error);
			toast.error('The download from YouTube failed');
		}).finally(() => {
			this.setState({ downloading: false });
		});
	}

	render() {
		const buttonClass = this.state.downloading ? 'display-none' : '';
		const loaderClass = this.state.downloading ? '' : 'display-none';
		const title = this.state.downloading ? '' : 'Download from YouTube';

		return (
			<div className="vertical-center" onClick={() => this.setModalOpen(true)}>
				<div className="icon-container">
					<i className={`${buttonClass} fab fa-youtube`} title={`${title}`}>
						<Modal isOpen={this.state.modalOpen} closeFunction={() => this.setModalOpen(false)}>
							<form className="form-modal" onSubmit={this.submitDownloadForm.bind(this)}>
								<div className="flex-label">
									<label htmlFor="song-url">URL</label>
									<input id="song-url" name="song-url" type="text" required/>
								</div>

								<h4>Optional Metadata</h4>

								<div className="flex-label">
									<label htmlFor="song-name">Name</label>
									<input id="song-name" name="song-name" type="text"/>
								</div>

								<div className="flex-label">
									<label htmlFor="song-artist">Artist</label>
									<input id="song-artist" name="song-artist" type="text"/>
								</div>

								<div className="flex-label">
									<label htmlFor="song-featuring">Featuring</label>
									<input id="song-featuring" name="song-featuring" type="text"/>
								</div>

								<div className="flex-label">
									<label htmlFor="song-album">Album</label>
									<input id="song-album" name="song-album" type="text"/>
								</div>

								<div className="flex-label">
									<label htmlFor="song-year">Release Year</label>
									<input id="song-year" name="song-year" type="text"/>
								</div>

								<div className="flex-label">
									<label htmlFor="song-track-number">Track Number</label>
									<input id="song-track-number" name="song-track-number" type="text"/>
								</div>

								<div className="flex-label">
									<label htmlFor="song-genre">Genre</label>
									<input id="song-genre" name="song-genre" type="text"/>
								</div>

								<hr/>
								<div>
									Crop Art to Square?
									<input
										id="crop-yes"
										type="radio"
										name="crop"
										value="true"
										checked={this.state.cropArtToSquare}
										onChange={() => this.setState({ cropArtToSquare: true })}
									/>
									<label htmlFor="crop-yes">Yes</label>

									<input
										id="crop-no"
										type="radio"
										name="crop"
										value="false"
										checked={!this.state.cropArtToSquare}
										onChange={() => this.setState({ cropArtToSquare: false })}
									/>
									<label htmlFor="crop-no">No</label>
								</div>
								<button>Download Song</button>
							</form>
						</Modal>
					</i>
					<img src="./images/ajax-loader.gif" className={`${loaderClass}`}/>
				</div>
			</div>
		)
	}
}
YoutubeDlButton.contextType = MusicContext;
