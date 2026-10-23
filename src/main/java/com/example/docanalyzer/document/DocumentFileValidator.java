package com.example.docanalyzer.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class DocumentFileValidator {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

	private static final int MAX_TEXT_BYTES = 2 * 1024 * 1024;
	private static final int MAX_ZIP_ENTRIES = 10_000;

	private static final Map<String, String> TEXT_TYPES = Map.ofEntries(Map.entry("txt", "text/plain"),
			Map.entry("log", "text/plain"), Map.entry("md", "text/markdown"), Map.entry("csv", "text/csv"),
			Map.entry("json", "application/json"), Map.entry("jolt", "application/json"),
			Map.entry("xml", "application/xml"), Map.entry("xslt", "application/xslt+xml"),
			Map.entry("xsl", "application/xslt+xml"), Map.entry("html", "text/html"), Map.entry("htm", "text/html"),
			Map.entry("yaml", "application/yaml"), Map.entry("yml", "application/yaml"),
			Map.entry("toml", "application/toml"), Map.entry("sql", "application/sql"),
			Map.entry("java", "text/x-java-source"), Map.entry("js", "text/javascript"), Map.entry("css", "text/css"),
			Map.entry("properties", "text/plain"));

	private DocumentFileValidator() {
	}

	public static String validate(MultipartFile file) throws IOException {

		if (file == null || file.isEmpty()) {
			throw error("EMPTY_FILE", "The uploaded file is empty.");
		}

		String filename = file.getOriginalFilename();

		if (filename == null || filename.isBlank()) {
			throw error("INVALID_FILENAME", "A filename is required.");
		}

		// Strip path components; never use a client path as a storage path.
		String safeName = filename.replace('\\', '/');
		safeName = safeName.substring(safeName.lastIndexOf('/') + 1);

		int dot = safeName.lastIndexOf('.');
		if (dot <= 0 || dot == safeName.length() - 1) {
			throw error("UNSUPPORTED_FILE_TYPE", "A supported file extension is required.");
		}

		String ext = safeName.substring(dot + 1).toLowerCase(Locale.ROOT);

		byte[] content = readBounded(file, MAX_TEXT_BYTES);

		// Binary formats first.
		if (starts(content, 0x25, 0x50, 0x44, 0x46, 0x2D)) {
			require(ext.equals("pdf"), "File extension does not match PDF content.");
			return "application/pdf";
		}

		if (starts(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
			require(ext.equals("png"), "File extension does not match PNG content.");
			return "image/png";
		}

		if (starts(content, 0xFF, 0xD8, 0xFF)) {
			require(ext.equals("jpg") || ext.equals("jpeg"), "File extension does not match JPEG content.");
			return "image/jpeg";
		}

		if (startsAscii(content, "GIF87a") || startsAscii(content, "GIF89a")) {
			require(ext.equals("gif"), "File extension does not match GIF content.");
			return "image/gif";
		}

		if (startsAscii(content, "BM")) {
			require(ext.equals("bmp"), "File extension does not match BMP content.");
			return "image/bmp";
		}

		if (startsAscii(content, "RIFF") && content.length >= 12 && asciiAt(content, 8, "WEBP")) {
			require(ext.equals("webp"), "File extension does not match WebP content.");
			return "image/webp";
		}

		// ZIP-based formats: inspect their internal entries.
		if (isZip(content)) {
			String officeType = inspectZip(content, ext);
			if (officeType != null) {
				return officeType;
			}

			if (Set.of("zip").contains(ext)) {
				return "application/zip";
			}

			throw error("INVALID_DOCUMENT", "The ZIP contents do not match a supported document format.");
		}

		// Legacy DOC/XLS/PPT share the OLE signature. A signature
		// alone cannot determine which legacy Office format is present.
		if (starts(content, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
			throw error("LEGACY_OFFICE_REQUIRES_PARSER", "Legacy Office files require format-specific validation.");
		}

		// GZIP
		if (starts(content, 0x1F, 0x8B)) {
			require(ext.equals("gz") || ext.equals("gzip"), "File extension does not match GZIP content.");
			return "application/gzip";
		}

		// JSON and JOLT specifications are JSON documents.
		if (ext.equals("json") || ext.equals("jolt")) {
			String text = decodeUtf8(content);
			JSON.readTree(text);

			if (ext.equals("jolt")) {
				// Syntax validation only. Validate operations against
				// your JOLT implementation before executing a spec.
				return "application/json";
			}

			return "application/json";
		}

		// XML and XSLT.
		if (Set.of("xml", "xsl", "xslt").contains(ext)) {
			org.w3c.dom.Document document = parseXml(content);

			if (ext.equals("xsl") || ext.equals("xslt")) {
				String namespace = document.getDocumentElement().getNamespaceURI();
				String root = document.getDocumentElement().getLocalName();

				require("http://www.w3.org/1999/XSL/Transform".equals(namespace)
						&& Set.of("stylesheet", "transform").contains(root),
						"The file is not a valid XSLT stylesheet.");

				return "application/xslt+xml";
			}

			return "application/xml";
		}

		// Text formats.
		if (TEXT_TYPES.containsKey(ext)) {
			String text = decodeUtf8(content);

			switch (ext) {
			case "yaml", "yml" -> YAML.readTree(text);
			case "toml" -> {
				// Add a TOML parser if full TOML syntax validation
				// is required. UTF-8 text validation alone is insufficient.
				require(!text.isBlank(), "The TOML file is empty.");
			}
			case "csv" -> require(!text.isBlank(), "The CSV file is empty.");
			case "html", "htm", "sql", "java", "js", "css", "txt", "md", "log", "properties" ->
				require(!text.isBlank(), "The text file is empty.");
			default -> {
			}
			}

			return TEXT_TYPES.get(ext);
		}

		throw error("UNSUPPORTED_FILE_TYPE", "This file type is not supported.");
	}

	private static String inspectZip(byte[] bytes, String ext) throws IOException {

		boolean contentTypes = false;
		boolean wordDocument = false;
		boolean workbook = false;
		boolean presentation = false;
		boolean macroProject = false;
		boolean odt = false;
		boolean ods = false;
		boolean odp = false;
		int entries = 0;

		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {

			ZipEntry entry;

			while ((entry = zip.getNextEntry()) != null) {
				if (++entries > MAX_ZIP_ENTRIES) {
					throw error("ARCHIVE_LIMIT_EXCEEDED", "The archive contains too many entries.");
				}

				String name = entry.getName();

				// Reject unsafe archive paths.
				if (name.startsWith("/") || name.contains("../") || name.equals("..") || name.contains("\\")) {
					throw error("INVALID_ARCHIVE", "The archive contains an unsafe path.");
				}

				contentTypes |= name.equals("[Content_Types].xml");
				wordDocument |= name.equals("word/document.xml");
				workbook |= name.equals("xl/workbook.xml");
				presentation |= name.equals("ppt/presentation.xml");
				macroProject |= name.equals("xl/vbaProject.bin");

				// OpenDocument files use a mimetype entry.
				if (name.equals("mimetype") && !entry.isDirectory()) {
					byte[] value = zip.readNBytes(256);
					String mime = new String(value, StandardCharsets.US_ASCII);

					odt |= mime.equals("application/vnd.oasis.opendocument.text");
					ods |= mime.equals("application/vnd.oasis.opendocument.spreadsheet");
					odp |= mime.equals("application/vnd.oasis.opendocument.presentation");
				}

				zip.closeEntry();
			}
		}

		if (contentTypes && wordDocument) {
			require(Set.of("docx", "docm").contains(ext), "Office document extension mismatch.");

			return ext.equals("docm") ? "application/vnd.ms-word.document.macroEnabled.12"
					: "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
		}

		if (contentTypes && workbook) {
			require(Set.of("xlsx", "xlsm", "xltx", "xltm").contains(ext), "Spreadsheet extension mismatch.");

			if (macroProject && !Set.of("xlsm", "xltm").contains(ext)) {
				throw error("FILE_TYPE_MISMATCH", "A macro-enabled spreadsheet requires an appropriate extension.");
			}

			return ext.equals("xlsm") ? "application/vnd.ms-excel.sheet.macroEnabled.12"
					: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
		}

		if (contentTypes && presentation) {
			require(Set.of("pptx", "pptm", "ppsx", "ppsm", "potx", "potm").contains(ext),
					"Presentation extension mismatch.");

			return ext.equals("pptm") ? "application/vnd.ms-powerpoint.presentation.macroEnabled.12"
					: "application/vnd.openxmlformats-officedocument.presentationml.presentation";
		}

		if (odt) {
			require(ext.equals("odt"), "File extension does not match ODT content.");
			return "application/vnd.oasis.opendocument.text";
		}

		if (ods) {
			require(ext.equals("ods"), "File extension does not match ODS content.");
			return "application/vnd.oasis.opendocument.spreadsheet";
		}

		if (odp) {
			require(ext.equals("odp"), "File extension does not match ODP content.");
			return "application/vnd.oasis.opendocument.presentation";
		}

		return null;
	}

	private static org.w3c.dom.Document parseXml(byte[] content) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

			factory.setNamespaceAware(true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

			return factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
		} catch (Exception ex) {
			throw error("INVALID_DOCUMENT", "The XML document is invalid or unsafe.");
		}
	}

	private static byte[] readBounded(MultipartFile file, int maximum) throws IOException {

		if (file.getSize() > maximum) {
			throw error("FILE_TOO_LARGE", "The file exceeds the configured validation limit.");
		}

		try (InputStream input = file.getInputStream()) {
			byte[] bytes = input.readNBytes(maximum + 1);

			if (bytes.length > maximum) {
				throw error("FILE_TOO_LARGE", "The file exceeds the configured validation limit.");
			}

			return bytes;
		}
	}

	private static String decodeUtf8(byte[] bytes) {
		String value = new String(bytes, StandardCharsets.UTF_8);

		if (value.indexOf('\u0000') >= 0 || value.indexOf('\uFFFD') >= 0) {
			throw error("INVALID_TEXT", "The file is not valid supported UTF-8 text.");
		}

		return value.startsWith("\uFEFF") ? value.substring(1) : value;
	}

	private static boolean isZip(byte[] b) {
		return starts(b, 0x50, 0x4B, 0x03, 0x04) || starts(b, 0x50, 0x4B, 0x05, 0x06)
				|| starts(b, 0x50, 0x4B, 0x07, 0x08);
	}

	private static boolean starts(byte[] b, int... signature) {
		if (b.length < signature.length) {
			return false;
		}

		for (int i = 0; i < signature.length; i++) {
			if ((b[i] & 0xFF) != signature[i]) {
				return false;
			}
		}

		return true;
	}

	private static boolean startsAscii(byte[] b, String signature) {
		return asciiAt(b, 0, signature);
	}

	private static boolean asciiAt(byte[] b, int offset, String signature) {

		byte[] expected = signature.getBytes(StandardCharsets.US_ASCII);

		if (b.length < offset + expected.length) {
			return false;
		}

		for (int i = 0; i < expected.length; i++) {
			if (b[offset + i] != expected[i]) {
				return false;
			}
		}

		return true;
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw error("FILE_TYPE_MISMATCH", message);
		}
	}

	private static UploadException error(String code, String message) {
		return new UploadException(code, message);
	}
}