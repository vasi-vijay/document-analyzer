package com.example.docanalyzer.question;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

	private static final Logger log = LoggerFactory.getLogger(QuestionController.class);

	private final QuestionService service;

	public QuestionController(QuestionService service) {
		this.service = service;
	}

	@PostMapping
	public QuestionResponse ask(@Valid @RequestBody QuestionRequest request, Authentication auth) {
		long startTime = System.nanoTime();
		log.info("Question request received.");
		try {
			QuestionResponse response = service.ask(auth.getName(), request);
			long durationMs = (System.nanoTime() - startTime) / 1_000_000;
			log.info("Question request completed successfully. durationMs={}", durationMs);
			return response;
		} catch (ResponseStatusException ex) {
			log.warn("Question request rejected. status={}, durationMs={}", ex.getStatusCode().value(),
					(System.nanoTime() - startTime) / 1_000_000);
			throw ex;
		} catch (RuntimeException ex) {
			log.error("Question request failed. durationMs={}, exceptionType={}",
					(System.nanoTime() - startTime) / 1_000_000, ex.getClass().getSimpleName(), ex);
			throw ex;
		}
	}
}