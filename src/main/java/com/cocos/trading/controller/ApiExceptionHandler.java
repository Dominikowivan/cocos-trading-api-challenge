package com.cocos.trading.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.cocos.trading.service.UnknownUserException;

@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(UnknownUserException.class)
	ProblemDetail unknownUser(UnknownUserException exception) {
		return problem(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ProblemDetail malformedIdentifier(MethodArgumentTypeMismatchException exception) {
		return problem(HttpStatus.BAD_REQUEST,
				"Failed to convert '" + exception.getName() + "' with value: '" + exception.getValue() + "'");
	}

	private static ProblemDetail problem(HttpStatus status, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(status.getReasonPhrase());
		return problem;
	}

}
