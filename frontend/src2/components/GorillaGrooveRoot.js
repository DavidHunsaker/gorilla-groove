import React from 'react';
import {BrowserRouter, Redirect, Route, Switch} from 'react-router-dom';
import Login from './Login/Login';
import MainPage from './MainPage/MainPage';
import {isLoggedIn} from '../util';


const GorillaGrooveRoot = ({onLeaveBeta}) => {
	const PrivateRoute = (props) => {
		if (!isLoggedIn()) {
			return <Redirect to={'/login'}/>;
		}
		return <Route {...props} />;
	};

	return (
		<>
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
					<Route path='/login' component={Login}/>
					<PrivateRoute path='/' component={MainPage}/>
					<Route render={() => <h1>Yo dawg where the page at</h1>}/>
				</Switch>
			</BrowserRouter>
		</>
	);
};

export default GorillaGrooveRoot;