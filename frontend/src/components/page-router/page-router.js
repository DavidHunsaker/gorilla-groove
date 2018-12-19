import React from 'react';
import {BrowserRouter, Route, Switch} from 'react-router-dom'
import {LoginPage, SiteLayout} from "../../components";
import {MusicProvider} from "../../services/music-provider";

export function PageRouter() {
	return (
		<MusicProvider>
			<BrowserRouter>
				<Switch>
					<Route path="/login" component={LoginPage}/>
					<Route path="/" component={SiteLayout}/>
					<Route render={() => <h1>Yo dawg where the page at</h1>}/>
				</Switch>
			</BrowserRouter>
		</MusicProvider>
	)
}
