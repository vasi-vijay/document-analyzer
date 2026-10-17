package com.example.docanalyzer.question;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiModelService {
	private final ObjectProvider<OpenAiChatModel> openAiModels;
	private final ObjectProvider<OllamaChatModel> ollamaModels;
	private final String provider;

	public AiModelService(ObjectProvider<OpenAiChatModel> openAiModels, ObjectProvider<OllamaChatModel> ollamaModels,
			@Value("${app.ai.provider:ollama}") String provider) {
		this.openAiModels = openAiModels;
		this.ollamaModels = ollamaModels;
		this.provider = provider.toLowerCase();
	}

	public String answer(String systemPrompt, String userPrompt) {
		ChatClient client;
		if (provider.equals("ollama")) {
			OllamaChatModel model = ollamaModels.getIfAvailable();
			if (model == null)
				throw new IllegalStateException(
						"Ollama model is not configured. Set OLLAMA_BASE_URL and OLLAMA_MODEL.");
			client = ChatClient.create(model);
		} else if (provider.equals("openai")) {
			OpenAiChatModel model = openAiModels.getIfAvailable();
			if (model == null)
				throw new IllegalStateException(
						"OpenAI-compatible model is not configured. Set OPENAI_API_KEY, OPENAI_BASE_URL and OPENAI_MODEL.");
			client = ChatClient.create(model);
		} else {
			throw new IllegalStateException("Unsupported app.ai.provider: " + provider + ". Use 'ollama' or 'openai'.");
		}
		String content = client.prompt().system(systemPrompt).user(userPrompt).call().content();
		if (content == null || content.isBlank())
			throw new IllegalStateException("The configured model returned an empty response.");
		return content.trim();
	}

	public String provider() {
		return provider;
	}
}
