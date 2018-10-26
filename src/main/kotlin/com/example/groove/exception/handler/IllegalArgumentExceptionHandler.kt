@file:Suppress("unused")

package com.example.groove.exception.handler

import org.apache.tomcat.util.http.fileupload.FileUploadBase
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.context.request.WebRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException


@ControllerAdvice
class IllegalArgumentExceptionHandler : ResponseEntityExceptionHandler() {

	// These exceptions should return a 400 to the client
	@ExceptionHandler(
			IllegalArgumentException::class,
			FileUploadBase.FileSizeLimitExceededException::class,
			MaxUploadSizeExceededException::class
	)
	protected fun handleIllegalArgument(ex: RuntimeException, request: WebRequest): ResponseEntity<Any> {
		return handleExceptionInternal(ex, ex.message, HttpHeaders(), HttpStatus.BAD_REQUEST, request)
	}
}