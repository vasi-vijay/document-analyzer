package com.example.docanalyzer.document;

public class UploadException extends RuntimeException {
	private final String code;

	public UploadException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
