import React, {useState} from 'react';
import {Redirect, withRouter} from 'react-router-dom';
import {getDeviceIdentifier} from '../../../src/services/version';
import {DeviceType} from '../../../src/enums/device-type';
import {addCookie} from '../../util/cookie';
import {Api} from '../../util/api';
import {isLoggedIn} from '../../util';


const Login = ({history}) => {
	if (isLoggedIn()) {
		return <Redirect to={'/'}/>;
	}
	const [forgotPassword, setForgotPassword] = useState(false);


	const submitLogin = async (event) => {
		event.preventDefault();
		event.stopPropagation();

		const form = event.target;

		const params = {
			email: form.email.value,
			password: form.password.value,
			deviceId: getDeviceIdentifier(),
			version: __VERSION__,
			deviceType: DeviceType.WEB
		};

		try {
			const {token, email} = await Api.post('authentication/login', params);
			const ninetyDays = 7776000;
			addCookie('cookieToken', token, ninetyDays);
			addCookie('loggedInEmail', email, ninetyDays);
			history.push('/');
		} catch (error) {
			console.error(error);
		}
	};

	// const submitPasswordReset = (event) => {
	// 	event.preventDefault();
	// 	event.stopPropagation();
	// }

	const renderLoginForm = () => {
		return (
			<>
				<form onSubmit={submitLogin}>
					<div className={'form-group'}>
						<label htmlFor={'email'}>Email</label>
						<input id={'email'} name={'email'} type={'email'}/>
					</div>
					<div className={'form-group'}>
						<label htmlFor={'password'}>Password</label>
						<input id={'password'} name={'password'} type={'password'}/>
					</div>
					<div className={'form-buttons'}>
						<button className={'primary'} type={'submit'}>Let's groove</button>
					</div>
				</form>
				<button className={'link'} onClick={() => setForgotPassword(true)}>Forgot your password?</button>
			</>
		);
	};

	const renderForgotPasswordForm = () => {
		return (
			<>
				<p>
					J/K this doesn't work yet, come back later.
				</p>
				<button className={'link'} onClick={() => setForgotPassword(false)}>Go back</button>
			</>
		);
	};

	return (
		<div className={'login'}>
			<div>
				<h1>Gorilla Groove</h1>
				<h2>Ultimate</h2>
				{forgotPassword ? renderForgotPasswordForm() : renderLoginForm()}
			</div>
		</div>
	);
};

export default withRouter(Login);