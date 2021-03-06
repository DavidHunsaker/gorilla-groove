import React from 'react';
import {MusicContext} from "../../services/music-provider";
import {Modal} from "../modal/modal";
import {toast} from "react-toastify";
import {LoadingSpinner} from "../loading-spinner/loading-spinner";

export class SongProperties extends React.Component {
	constructor(props) {
		super(props);

		this.state = {
			modalOpen: false,
			loading: false,
			name: '',
			artist: '',
			featuring: '',
			album: '',
			genre: '',
			trackNumber: '',
			releaseYear: '',
			bitRate: '',
			sampleRate: '',
			note: '',
			albumArt: null,
			albumArtUrl: '',
			cropArtToSquare: false
		};

		this.inputNames = ['name', 'artist', 'featuring', 'album', 'genre', 'trackNumber',
			'releaseYear', 'bitRate', 'sampleRate', 'note'];
	}

	componentDidUpdate(prevProps, prevState) {
		if (this.state.modalOpen && !prevState.modalOpen) {
			this.initializeInputs();
		}
	}

	initializeInputs() {
		const tracks = this.props.getSelectedTracks();
		const newState = {};
		newState.originalValues = {}; // Keep track of original values so we know what to actually update when saving

		let initializeKey = (key) => {
			return tracks.every(track => track[key] === tracks[0][key]) ? tracks[0][key] : '';
		};

		this.inputNames.forEach(inputName => {
			const originalValue = initializeKey(inputName);
			newState[inputName] = originalValue;
			newState.originalValues[inputName] = originalValue;
		});

		newState.albumArtUrl = './images/unknown-art.jpg';
		newState.albumArt = null;
		newState.cropArtToSquare = false;

		this.setState(newState);
	}

	setModalOpen(isOpen) {
		this.setState({ modalOpen: isOpen })
	}

	inputChange(stateName, event) {
		const newState = {};
		newState[stateName] = event.target.value;

		this.setState(newState);
	}

	updateTracks(event) {
		event.preventDefault();

		const changedProperties = this.getChangedProperties();

		if (Object.keys(changedProperties).length === 0 && this.state.albumArt === null) {
			toast.info("No track data changes were made");
			this.setState({ modalOpen: false });
			return;
		}

		this.setState({ loading: true });

		this.context.updateTracks(
			this.props.getSelectedTracks(),
			this.state.albumArt,
			changedProperties
		).then(() => {
			this.setState({ modalOpen: false });
			this.context.forceTrackUpdate();
			toast.success("Track data updated successfully");
		}).finally(() => this.setState({ loading: false }));
	}

	getChangedProperties() {
		const changedProperties = {};

		this.inputNames.forEach(inputName => {
			const originalValue = this.state.originalValues[inputName];
			const currentValue = this.state[inputName];

			if (currentValue !== originalValue) {
				changedProperties[inputName] = currentValue;
			}
		});

		if (this.state.cropArtToSquare) {
			changedProperties.cropArtToSquare = true;
		}

		return changedProperties;
	}

	// noinspection JSMethodCanBeStatic
	openFileDialog() {
		document.getElementById('picture-upload').click();
	}

	handlePictureUpload(inputEvent) {
		let file = inputEvent.target.files[0];

		// Preview the the album art for the user before upload
		let reader = new FileReader();
		reader.onload = (e) => {
			this.setState({
				albumArt: file,
				albumArtUrl: e.target.result
			});
		};

		reader.readAsDataURL(file);
	}

	render() {
		// noinspection HtmlUnknownTarget
		return (
			<div onClick={() => this.setModalOpen(true)}>
				Properties
				<Modal
					isOpen={this.state.modalOpen}
					closeFunction={() => this.setModalOpen(false)}
				>
					<div id="song-properties" className="form-modal">
						<LoadingSpinner visible={this.state.loading}/>

						<form onSubmit={e => this.updateTracks(e)}>

							<h2>Track Properties</h2>

							<div className="flex-label">
								<label htmlFor="property-name">Name</label>
								<input
									id="property-name"
									name="property-name"
									className="long-property"
									type="text"
									onChange={(e) => this.inputChange('name', e)}
									value={this.state.name}
								/>
							</div>

							<div className="flex-label">
								<label htmlFor="property-artist">Artist</label>
								<input
									id="property-artist"
									name="property-artist"
									className="long-property"
									type="text"
									onChange={(e) => this.inputChange('artist', e)}
									value={this.state.artist}
								/>
							</div>

							<div className="flex-label">
								<label htmlFor="property-featuring">Featuring</label>
								<input
									id="property-featuring"
									name="property-featuring"
									className="long-property"
									type="text"
									onChange={(e) => this.inputChange('featuring', e)}
									value={this.state.featuring}
								/>
							</div>

							<div className="flex-label">
								<label htmlFor="property-album">Album</label>
								<input
									id="property-album"
									name="property-album"
									className="long-property"
									type="text"
									onChange={(e) => this.inputChange('album', e)}
									value={this.state.album}
								/>
							</div>


							<div className="album-wrapper">

								<div>
									<div className="flex-label">
										<label htmlFor="property-genre">Genre</label>
										<input
											id="property-genre"
											name="property-genre"
											className="medium-property"
											type="text"
											onChange={(e) => this.inputChange('genre', e)}
											value={this.state.genre}
										/>
									</div>
									<div className="flex-label">
										<label htmlFor="property-track-num">Track #</label>
										<input
											id="property-track-num"
											name="property-track-num"
											className="short-property"
											type="text"
											onChange={(e) => this.inputChange('trackNumber', e)}
											value={this.state.trackNumber}
										/>
									</div>
									<div className="flex-label">
										<label htmlFor="property-year">Year</label>
										<input
											id="property-year"
											name="property-year"
											className="short-property"
											type="text"
											onChange={(e) => this.inputChange('releaseYear', e)}
											value={this.state.releaseYear}
										/>
									</div>
									<div className="flex-label">
										<label htmlFor="property-bit-rate">Bit Rate</label>
										<input
											id="property-bit-rate"
											name="property-bit-rate"
											className="short-property"
											type="text"
											value={this.state.bitRate}
											disabled
										/>
									</div>
									<div className="flex-label">
										<label htmlFor="property-sample-rate">Sample Rate</label>
										<input
											id="property-sample-rate"
											name="property-sample-rate"
											className="short-property"
											type="text"
											value={this.state.sampleRate}
											disabled
										/>
									</div>

									<hr/>
									<div className="flex-label">
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
								</div>

								<div
									className="album-art"
									style={{ backgroundImage: 'url(' + this.state.albumArtUrl + ')' }}
									onClick={this.openFileDialog.bind(this)}
								/>
								<input
									type="file"
									id="picture-upload"
									className="display-none"
									onChange={this.handlePictureUpload.bind(this)}
								/>

							</div>
							<div className="property-note">
								Note
								<textarea
									value={this.state.note}
									onChange={e => this.inputChange('note', e)}
								/>
							</div>

							<div>
								<button type="submit">Save</button>
							</div>

						</form>
					</div>

				</Modal>
			</div>
		)
	}
}
SongProperties.contextType = MusicContext;
