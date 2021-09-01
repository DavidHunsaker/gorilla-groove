import React from 'react'
import {Link, useRouteMatch} from 'react-router-dom'


const SideNavLink = ({to, children}) => {
	let match = useRouteMatch({path: to})
	return (
		<div className={'navigable-item'}>
			<Link to={to} className={match ? 'current' : null}>{children}</Link>
		</div>
	)
}

export default SideNavLink