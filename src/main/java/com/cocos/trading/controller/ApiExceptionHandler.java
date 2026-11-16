package com.cocos.trading.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.cocos.trading.service.search.InvalidSearchRequestException;
import com.cocos.trading.service.portfolio.UnknownUserException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiExceptionHandler {

	@ExceptionHandler(UnknownUserException.class)
	ProblemDetail unknownUser(UnknownUserException exception) {
		return problem(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(InvalidSearchRequestException.class)
	ProblemDetail invalidSearchRequest(InvalidSearchRequestException exception) {
		return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ProblemDetail missingParameter(MissingServletRequestParameterException exception) {
		return problem(HttpStatus.BAD_REQUEST,
				"Required request parameter '" + exception.getParameterName() + "' is not present");
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
