package com.cocos.trading.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.cocos.trading.model.user.UnknownUserException;
import com.cocos.trading.service.search.InvalidSearchRequestException;
import com.cocos.trading.service.submission.IdempotencyConflictException;
import com.cocos.trading.service.submission.InvalidSubmissionException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(UnknownUserException.class)
	ProblemDetail unknownUser(UnknownUserException exception) {
		return problem(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(InvalidSearchRequestException.class)
	ProblemDetail invalidSearchRequest(InvalidSearchRequestException exception) {
		return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(InvalidSubmissionException.class)
	ProblemDetail invalidSubmission(InvalidSubmissionException exception) {
		return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(IdempotencyConflictException.class)
	ProblemDetail idempotencyConflict(IdempotencyConflictException exception) {
		return problem(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail malformedBody(HttpMessageNotReadableException exception) {
		log.debug("unreadable request body: {}", exception.getMessage());
		return problem(HttpStatus.BAD_REQUEST, "The request body is not a readable order submission");
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

	@ExceptionHandler({ QueryTimeoutException.class, TransactionTimedOutException.class,
			CannotCreateTransactionException.class })
	ProblemDetail databaseUnavailable(Exception exception) {
		log.warn("database did not answer in time: {}", exception.getMessage());
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily unavailable, try again later");
	}

	private static ProblemDetail problem(HttpStatus status, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(status.getReasonPhrase());
		return problem;
	}

}
