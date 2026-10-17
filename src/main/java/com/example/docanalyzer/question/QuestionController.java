package com.example.docanalyzer.question;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {
	private final QuestionService service;

	public QuestionController(QuestionService service) {
		this.service = service;
	}

	@PostMapping
	public QuestionResponse ask(@Valid @RequestBody QuestionRequest request, Authentication auth) {
		return service.ask(auth.getName(), request);
	}
}
