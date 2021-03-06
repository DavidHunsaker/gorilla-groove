import React from 'react';
import ColumnResizer from 'column-resizer';
import * as ReactDOM from "react-dom";
import {SongRow} from "../song-row/song-row";
import {MusicContext} from "../../services/music-provider";
import SongPopoutMenu from "../popout-menu/song-popout-menu/song-popout-menu";
import {TrackView} from "../../enums/site-views";
import * as LocalStorage from "../../local-storage";
import {LoadingSpinner} from "../loading-spinner/loading-spinner";
import {displayKeyToTrackKey} from "../../util";


let doubleClickTimeout = null;
let withinDoubleClick = false;
let pendingEditableCell = null;

export class TrackList extends React.Component {
	constructor(props) {
		super(props);

		this.state = {
			selected: {}, // FIXME Pretty sure I should have made this a set. Pretty sure it's being used like a set
			firstSelectedIndex: null,
			lastSelectedIndex: null,
			editableCell: null,
			id: this.props.trackView ? 'track-list' : 'now-playing-list',
			contextMenuOptions: {
				expanded: false,
				x: 0,
				y: 0
			}
		};
	}

	componentDidMount() {
		this.enableResize();

		const tableBody = document.querySelector(`#${this.state.id} .track-list-table-body`);
		tableBody.addEventListener('mousedown', this.handleContextMenu.bind(this));

		// Only the trackView has editable cells. The other view doesn't need to worry about closing them
		if (this.props.trackView) {
			document.body.addEventListener('mousedown', this.handleEditStop.bind(this));
			document.body.addEventListener('keydown', this.handleKeyPress.bind(this));
			document.getElementsByClassName('border-layout-center')[0]
				.addEventListener('scroll', this.handleScroll.bind(this));
		}
		tableBody.addEventListener('contextmenu', this.suppressContextMenu.bind(this));
	}

	componentWillUnmount() {
		this.disableResize();

		if (!this.props.trackView) {
			document.body.removeEventListener('mousedown', this.handleEditStop);
			document.body.removeEventListener('keydown', this.handleKeyPress);
			document.getElementsByClassName('border-layout-center')[0]
				.removeEventListener('scroll', this.handleScroll);
		}

		ReactDOM.findDOMNode(this).removeEventListener('mousedown', this.handleContextMenu.bind(this));
		ReactDOM.findDOMNode(this).removeEventListener('contextmenu', this.suppressContextMenu.bind(this));
	}

	suppressContextMenu(event) {
		event.preventDefault();
	}

	enableResize() {
		if (!this.props.trackView) {
			return;
		}

		// noinspection JSUnusedGlobalSymbols
		const options = {
			resizeMode: 'overflow',
			serialize: true,
			onResize: () => {
				// Put this in a timeout so that the library has time to update session storage
				// We instead want to put it in local storage so it isn't lost as easily
				setTimeout(() => {
					let widths = sessionStorage.getItem('track-list').split(';').map(width => parseFloat(width));
					LocalStorage.setObject('column-widths', widths);
				}, 1000);
			},
			widths: LocalStorage.getObject('column-widths')
		};

		if (!this.resizer) {
			let tableElement = ReactDOM.findDOMNode(this).querySelector('#' + this.state.id);
			this.resizer = new ColumnResizer(tableElement, options);
		} else {
			this.resizer.reset(options);
		}
	}

	disableResize() {
		if (!this.props.trackView) {
			return;
		}

		if (this.resizer) {
			this.resizer.reset({ disable: true });
		}
	}

	handleEditStop(event) {
		if (event.target.id !== this.state.editableCell) {
			this.stopCellEdits();
		}
	}

	handleContextMenu(event) {
		if (event.button !== 2 || event.shiftKey) {
			return;
		}

		// When we right click, a click event is also fired to deal with selecting things.
		// Wrap the context menu in a timeout, because it needs to happen after the selection has resolved
		setTimeout(() => {
				this.setState({
					contextMenuOptions: {
						expanded: true,
						trackView: this.props.trackView ? this.context.trackView : TrackView.NOW_PLAYING,
						x: event.clientX,
						y: event.clientY + 4
					}}
				);
			}
		);
	}

	handleKeyPress(event) {
		// In react the native DOM events trigger completely, and then React synthetic events are fired afterwards.
		// Essentially, Any React key handler where we push enter is going to fire after this native document listener does.
		// We wrap it in a setTimeout, which will give the React event handlers a chance to set a property on the event
		// (propagationStopped) which we can then use to conditionally ignore the event
		setTimeout(() => {
			if (!event.propagationStopped) {
				this.handleKeyPressInternal(event);
			}
		});
	}

	handleKeyPressInternal(event) {
		// We are editing song data right now, so just ignore any other key actions
		if (this.state.editableCell) {
			return;
		}

		if (event.key === 'Enter') {
			let indexes = Object.keys(this.state.selected);
			if (indexes.length === 1) {
				this.context.playFromTrackIndex(parseInt(indexes[0]), true);
			} else {
				let tracks = indexes.map(index => this.props.userTracks[index]);
				this.context.playTracks(tracks);
			}
		} else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
			if (this.state.lastSelectedIndex === null) {
				return;
			}

			let newIndex = this.state.lastSelectedIndex + (event.key === 'ArrowDown' ? 1 : -1);

			if (newIndex >= this.props.userTracks.length || newIndex < 0) {
				return;
			}

			const selected = {};
			selected[newIndex] = true;
			this.setState({ selected: selected, lastSelectedIndex: newIndex });

			const trackList = document.getElementById('center-view');
			const selectedRow = trackList.querySelectorAll('.song-row')[newIndex];

			if (event.key === 'ArrowDown') {
				const newScroll = selectedRow.offsetTop - trackList.offsetHeight + 30;
				if (newScroll > trackList.scrollTop) {
					trackList.scrollTop = newScroll + selectedRow.offsetHeight - 10;
				}
			} else {
				if (selectedRow.offsetTop - selectedRow.offsetHeight + 13 < trackList.scrollTop) {
					trackList.scrollTop = selectedRow.offsetTop - selectedRow.offsetHeight;
				}
			}
		}
	}

	handleScroll() {
		const scrollBuffer = 500; // Amount of space to scroll to in order to start loading more tracks

		const container = document.getElementsByClassName('border-layout-center')[0];
		if (!this.context.loadingTracks
			&& container.scrollHeight < container.offsetHeight + container.scrollTop + scrollBuffer
			&& this.context.viewedTracks.length < this.context.totalTracksToFetch) {

			this.context.loadMoreTracks();
		}
	}

	handleRowClick(event, userTrackIndex) {
		const isLeftClick = event.type === 'mousedown' && event.button === 0;
		if (!isLeftClick && event.shiftKey) {
			return;
		}

		if (event.target.tagName === 'INPUT') {
			return; // If we clicked an input just ignore the click entirely since we were editing a song
		}

		let selected = this.state.selected;
		const newState = { lastSelectedIndex: userTrackIndex };

		// Always set the first selected if there wasn't one
		if (this.state.firstSelectedIndex === null) {
			newState.firstSelectedIndex = userTrackIndex;
		}

		// If we aren't holding a modifier, we want to deselect all rows that were selected, and remember the track we picked
		// Additionally, if right clicking, we don't want to deselect if we selected an already selected row
		if (!event.ctrlKey && !event.shiftKey && !(!isLeftClick && selected[userTrackIndex])) {
			selected = {};
			newState.firstSelectedIndex = userTrackIndex;
		}

		// If we're holding shift, we should select the rows between this click and the first click
		if (event.shiftKey && this.state.firstSelectedIndex !== null) {
			selected = {};
			let startingRow = Math.min(this.state.firstSelectedIndex, userTrackIndex);
			let endingRow = Math.max(this.state.firstSelectedIndex, userTrackIndex);
			if (startingRow < endingRow) {
				endingRow++
			}

			for (let i = startingRow; i < endingRow; i++) {
				selected[i] = true;
			}
		}

		// The track we clicked needs to always be selected
		selected[userTrackIndex] = true;

		if (isLeftClick) {
			// Whenever we start a new double click timer, make sure we cancel any old ones lingering about
			// TODO I don't actually make sure you double clicked the SAME row twice. Just that you double clicked. Fix perhaps?
			if (withinDoubleClick) {
				this.context.playFromTrackIndex(userTrackIndex, this.props.trackView);
				newState.editableCell = null;
				this.cancelDoubleClick();
			} else {
				this.cancelDoubleClick();
				const pendingEditableCell = this.setupEdit(event, selected, userTrackIndex);
				this.setupDoubleClick(pendingEditableCell);
			}

		}

		newState.selected = selected;
		this.setState(newState);
	}

	setupDoubleClick(pendingEditableCell) {
		// Set up a timer that will kill our ability to double click if we are too slow
		doubleClickTimeout = setTimeout(() => {
			withinDoubleClick = false;
			if (pendingEditableCell) {
				this.setState({ editableCell: pendingEditableCell });
				pendingEditableCell = null;
			}
		}, 300);
		withinDoubleClick = true;
	}

	setupEdit(event, selectedIndexes, userTrackIndex) {
		// If we ALREADY had exactly one thing selected and we clicked the same thing, mark the cell
		// so that it will be edited if the user doesn't click a second time
		if (Object.keys(selectedIndexes).length === 1 && this.state.firstSelectedIndex === userTrackIndex) {

			// If a table cell is empty, it clicks on a different element. So just make sure we grab the ID, either
			// from the current element, or one of its children if the child node was empty and thus not clicked on
			let cellId = null;
			if (event.target.id) {
				cellId = event.target.id;
			} else {
				const elements = event.target.querySelectorAll('[id]');
				cellId = elements ? elements[0].id : null;
			}

			pendingEditableCell = cellId;

			return cellId;
		} else {
			pendingEditableCell = null;
			this.setState({ editableCell: null });

			return null;
		}
	}

	cancelDoubleClick() {
		if (doubleClickTimeout) {
			window.clearTimeout(doubleClickTimeout);

			withinDoubleClick = false;
			doubleClickTimeout = null;
			pendingEditableCell = null;
		}
	}

	stopCellEdits() {
		if (this.state.editableCell !== null) {
			this.setState({ editableCell: null })
		}
	}

	getSelectedTracks() {
		// This can happen if we are attempting to get the selected tracks during a reload (like after deleting)
		if (this.props.userTracks.length === 0) {
			return [];
		}

		return Object.keys(this.state.selected).map(index => this.props.userTracks[index]);
	}

	getSelectedTrackIndexes() {
		return Object.keys(this.state.selected).map(index => parseInt(index));
	}

	handleHeaderClick(event) {
		// We don't want the 'now playing' view to be able to sort things (at least for now). It gets messy
		if (!this.props.trackView || event.button !== 0) {
			return;
		}

		const columnDisplayName = event.currentTarget.querySelector('.column-name').innerHTML.trim();
		const columnName = displayKeyToTrackKey(columnDisplayName);

		const currentSort = this.context.currentSort.filter(sort => sort.hidden !== true);

		// If we aren't holding the shift key, then we only care about setting or swapping the primary sort
		if (!event.shiftKey) {
			// We specifically want a normal click to "reset" the sort if there were any additional sorts specified.
			// So the first click will always be ascending if there were more than 1 sorts specified.
			const descending = currentSort[0].column === columnName && currentSort.length === 1 && currentSort[0].isAscending;
			return this.context.setSort([{
				column: columnName,
				isAscending: !descending
			}]);
		}

		const sortPriority = currentSort.findIndex(sort => sort.column === columnName);

		if (sortPriority === -1) { // Column we clicked isn't yet sorted on. Appended the new sort
			const newSort = currentSort.slice(0);
			newSort.push({ column: columnName, isAscending: true });
			return this.context.setSort(newSort);
		}

		const newSort = currentSort.slice(0);
		// First click is ascending, second is descending, third is removal (but ONLY if not selecting the primary sort)
		const ascending = newSort[sortPriority].isAscending;
		if (ascending || !ascending && sortPriority === 0) {
			newSort[sortPriority].isAscending = !ascending
		} else {
			newSort.splice(sortPriority, 1);
		}

		this.setState({
			selected: {},
			lastSelectedIndex: null
		});

		this.context.setSort(newSort);
	}

	closeContextMenu(event) {
		// This is probably a stupid way to do this, but if we clicked on a row that was expandable,
		// then we don't want to actually close the menu as clicking it should really have no effect
		const classes = event.target.classList;
		if (classes.contains('expandable-width') || classes.contains('expansion-caret')) {
			return;
		}

		if (this.state.contextMenuOptions.expanded && event.button === 0) {
			this.setState({ contextMenuOptions: { expanded: false }});
		}
	}

	getSortIndicator(columnName) {
		if (!this.props.trackView) {
			return <span/>;
		}

		const explicitSorts = this.context.currentSort.filter(sort => sort.hidden !== true);

		const sortPriority = explicitSorts.findIndex(sort =>
			sort.column === displayKeyToTrackKey(columnName) && sort.hidden !== true
		);

		if (sortPriority === -1) {
			return <span/>;
		}

		const sortCaret = this.context.currentSort[sortPriority].isAscending ? '▲' : '▼';
		const sortPriorityText = explicitSorts.length === 1 ? '' : `${sortPriority + 1}`;
		return <React.Fragment>
			{sortCaret}<span className="sort-priority-text">{sortPriorityText}</span>
		</React.Fragment>
	}

	render() {
		// noinspection HtmlUnknownTarget
		return (
			<div className="track-list">
				<div>
					<SongPopoutMenu
						closeContextMenu={this.closeContextMenu.bind(this)}
						getSelectedTracks={this.getSelectedTracks.bind(this)}
						getSelectedTrackIndexes={this.getSelectedTrackIndexes.bind(this)}
						expanded={this.state.contextMenuOptions.expanded}
						trackView={this.state.contextMenuOptions.trackView}
						x={this.state.contextMenuOptions.x}
						y={this.state.contextMenuOptions.y}
					/>
				</div>
				<table id={this.state.id} className="track-table">
					<thead>
					<tr>
						{this.props.columns.map((columnName, index) => {
							return <th key={index} onMouseDown={this.handleHeaderClick.bind(this)}>
								<div>
									<span className="column-name">{columnName}</span>
									<span className="sort-indicator">{this.getSortIndicator(columnName)}</span>
								</div>
							</th>
						})}
					</tr>
					</thead>
					<tbody className="track-list-table-body">
					{this.props.userTracks.map((userTrack, index) => {
						let played;
						if (this.props.trackView) {
							played = this.context.playedTrack && this.context.playedTrack.id === userTrack.id;
						} else {
							played = this.context.playedTrackIndex === index;
						}
						return (
							<SongRow
								key={index}
								columns={this.props.columns}
								rowIndex={index}
								editableCell={this.state.editableCell}
								played={played}
								userTrack={userTrack}
								selected={this.state.selected[index.toString()]}
								onClick={this.handleRowClick.bind(this)}
								stopCellEdits={this.stopCellEdits.bind(this)}
							/>
						);
					})}
					</tbody>
				</table>
				<LoadingSpinner
					visible={this.context.loadingTracks && this.props.trackView && this.props.userTracks.length === 0}
				/>
			</div>
		)
	};
}
TrackList.contextType = MusicContext;
