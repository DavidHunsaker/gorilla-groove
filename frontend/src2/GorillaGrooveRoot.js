import React from 'react'
import {BrowserRouter, Redirect, Route, Switch} from 'react-router-dom'

import {isLoggedIn} from './util'
import Login from './components/Login/Login'
import {GorillaGrooveProvider} from './context/GorillaGrooveContext'
import MainPage from './components/MainPage/MainPage'


const GorillaGrooveRoot = ({onLeaveBeta}) => {

	const PrivateRoute = (props) => {
		if (!isLoggedIn()) {
			return <Redirect to={'/login'} />
		}
		return <Route {...props} />
	}

	return (
		<GorillaGrooveProvider>
			<div className={'beta-warning'}>
				<p>
					You're currently using the beta version of <em>Gorilla Groove: Ultimate</em>.
				</p>
				<button className={'small'} onClick={onLeaveBeta}>
					Yeet
				</button>
			</div>
			<BrowserRouter>
				<Switch>
					<Route path="/login" component={Login} />
					<PrivateRoute path="/" component={MainPage} />
					<Route render={() => <h1>Yo dawg where the page at</h1>} />
				</Switch>
			</BrowserRouter>
		</GorillaGrooveProvider>
	)
}

export default GorillaGrooveRoot