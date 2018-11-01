import React from 'react';
import {BrowserRouter, Route, Switch} from 'react-router-dom'
import {LoginPage, LibraryLayout} from "../../components";

export function PageRouter() {
	return (
		<BrowserRouter>
			<Switch>
				<Route path="/login" component={LoginPage}/>
				<Route path="/" component={LibraryLayout}/>
				<Route render={() => <h1>Yo dawg where the page at</h1>}/>
			</Switch>
		</BrowserRouter>
	)
}