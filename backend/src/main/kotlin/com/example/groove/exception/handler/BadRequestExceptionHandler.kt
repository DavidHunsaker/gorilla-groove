@file:Suppress("unused")

package com.example.groove.exception.handler

import com.example.groove.exception.PermissionDeniedException
import com.example.groove.exception.ResourceNotFoundException
import org.apache.tomcat.util.http.fileupload.FileUploadBase
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


@ControllerAdvice
class BadRequestExceptionHandler {

	// These exceptions should return a 400
	@ExceptionHandler(
			IllegalArgumentException::class,
			FileUploadBase.FileSizeLimitExceededException::class,
			MaxUploadSizeExceededException::class
	)
	protected fun handleIllegalArgument(req: HttpServletRequest, ex: Exception, resp: HttpServletResponse) {
		return resp.sendError(HttpStatus.BAD_REQUEST.value(), ex.message)
	}

	// These exceptions should return a 404
	@ExceptionHandler(ResourceNotFoundException::class)
	protected fun handleNotFound(req: HttpServletRequest, ex: Exception, resp: HttpServletResponse) {
		return resp.sendError(HttpStatus.NOT_FOUND.value(), ex.message)
	}

	// These exceptions should return a 401
	@ExceptionHandler(PermissionDeniedException::class)
	protected fun handleUnauthorized(req: HttpServletRequest, ex: Exception, resp: HttpServletResponse) {
		return resp.sendError(HttpStatus.UNAUTHORIZED.value(), ex.message)
	}
}
