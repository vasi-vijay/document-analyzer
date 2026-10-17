package com.example.docanalyzer.document;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
	@ExceptionHandler(UploadException.class)
	ProblemDetail handleUpload(UploadException ex) {
		HttpStatus status = switch (ex.getCode()) {
		case "FILE_TOO_LARGE" -> HttpStatus.PAYLOAD_TOO_LARGE;
		case "UNSUPPORTED_FILE_TYPE" -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
		default -> HttpStatus.BAD_REQUEST;
		};
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
		problem.setProperty("code", ex.getCode());
		return problem;
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex) {
		ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
				"The file exceeds the configured upload limit.");
		p.setProperty("code", "FILE_TOO_LARGE");
		return p;
	}

	@ExceptionHandler(IllegalStateException.class)
	ProblemDetail handleModelConfiguration(IllegalStateException ex) {
		ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"The configured AI model is unavailable or incorrectly configured. Check APP_AI_PROVIDER and the model endpoint settings.");
		p.setProperty("code", "AI_PROVIDER_UNAVAILABLE");
		return p;
	}

	@ExceptionHandler(ResponseStatusException.class)
	ProblemDetail handleStatus(ResponseStatusException ex) {
		return ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
	}
}
