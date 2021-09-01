import React, {useContext, useState} from 'react'
import {Redirect, withRouter} from 'react-router-dom'
import {isLoggedIn} from '../../util'
import {GorillaGrooveContext} from '../../context/GorillaGrooveContext'


const Login = ({history}) => {
	if (isLoggedIn()) {
		return <Redirect to={'/'}/>
	}
	const [forgotPassword, setForgotPassword] = useState(false)

	const app = useContext(GorillaGrooveContext)

	const submitLogin = async (event) => {
		event.preventDefault()
		event.stopPropagation()

		const form = event.target
		// TODO: i don't like that this returns true
		if (await app.logIn(form.email.value, form.password.value)) {
			history.push('/')
		}
	}

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
		)
	}

	const renderForgotPasswordForm = () => {
		return (
			<>
				<p>
					J/K this doesn't work yet, come back later.
				</p>
				<button className={'link'} onClick={() => setForgotPassword(false)}>Go back</button>
			</>
		)
	}

	return (
		<div className={'login'}>
			<div>
				<h1>Gorilla Groove</h1>
				<h2>Ultimate</h2>
				{forgotPassword ? renderForgotPasswordForm() : renderLoginForm()}
			</div>
		</div>
	)
}

export default withRouter(Login)