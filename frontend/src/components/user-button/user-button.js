import React, {useContext} from 'react';
import {useHistory} from "react-router-dom";
import {Api} from "../../api";
import PopoutMenu from "../popout-menu/popout-menu";
import {Settings} from "../settings/settings";
import InviteUser from "./invite-user/invite-user";
import {deleteCookie} from "../../cookie";
import {DraftRelease} from "../draft-release/draft-release";
import {PermissionType} from "../../enums/permission-type";
import {UserContext} from "../../services/user-provider";
import TrackHistory from "../track-history/track-history";
import DeviceManagement from "../device-management/device-management";
import {toast} from "react-toastify";
import {MusicContext} from "../../services/music-provider";
import CrashReport from "../crash-report/crash-report";
import {clearMediaSession} from "../../media-key";
import * as LocalStorage from '../../local-storage'

const originalTitle = document.title;

export default function UserButton() {
	const userContext = useContext(UserContext);
	const musicContext = useContext(MusicContext);
	const history = useHistory();

	const logout = event => {
		document.title = originalTitle;

		event.preventDefault();
		Api.post('authentication/logout').catch(error => {
			console.error(error)
		}).finally(() => {
			deleteCookie('cookieToken');
			deleteCookie('loggedInEmail');

			clearMediaSession();

			musicContext.resetSessionState();
			history.push('/login'); // Redirect to the login page now that we logged out
		});
	};

	const runReviewQueues = () => {
		Api.post('review-queue/check-new-songs').then(() => {
			toast.success('New song downloads successful');
		}).catch(() => {
			toast.error('Failed to download new songs');
		})
	};

	const handleTurnOnBetaClient = () => {
		LocalStorage.setBoolean('beta-client', true);
		window.location.reload();
	}

	return (
		<div className="user-menu">
			<PopoutMenu
				mainItem={{
					className: "user-button",
					text: <i className="fas fa-bars"/>
				}}
				menuItems={[
					{ component: <Settings/> },
					{ component: <DeviceManagement/> },
					{ component: <TrackHistory/> },
					{ component: <InviteUser/>, shouldRender: userContext.hasPermission(PermissionType.INVITE_USER) },
					{ component: <DraftRelease/>, shouldRender: userContext.hasPermission(PermissionType.WRITE_VERSION_HISTORY) },
					{ component: <CrashReport/>, shouldRender: userContext.hasPermission(PermissionType.VIEW_CRASH_REPORTS) },
					{ text: "Run Review Queues", clickHandler: runReviewQueues, shouldRender: userContext.hasPermission(PermissionType.RUN_REVIEW_QUEUES) },
					{ text: "Use Beta Client", clickHandler: handleTurnOnBetaClient, shouldRender: userContext.hasPermission(PermissionType.EXPERIMENTAL) },
					{ text: "Logout", clickHandler: logout }
				]}
			/>
		</div>
	)
}
