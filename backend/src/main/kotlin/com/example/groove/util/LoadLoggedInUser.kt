package com.example.groove.util

import com.example.groove.db.model.User
import org.springframework.security.core.context.SecurityContextHolder

fun loadLoggedInUser(): User {
	SecurityContextHolder.getContext().authentication.details
	return SecurityContextHolder.getContext().authentication.principal as User
}
