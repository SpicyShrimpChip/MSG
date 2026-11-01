package com.ailms.backend.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 업로드 검사용 작은 파일 내용. 실제 강의자료·시험 문제는 쓰지 않고 직접 만든 최소 구조만 담는다.
 */
public final class TestFiles {

	private static final String PPTX_MAIN =
			"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml";
	private static final String PPTM_MAIN = "application/vnd.ms-powerpoint.presentation.macroEnabled.main+xml";
	private static final String DOCX_MAIN =
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";

	private TestFiles() {
	}

	public static byte[] pdf() {
		return "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n"
				.getBytes(StandardCharsets.US_ASCII);
	}

	public static byte[] pptx() {
		return zip(Map.of(
				"[Content_Types].xml", contentTypes(PPTX_MAIN, "/ppt/presentation.xml"),
				"ppt/presentation.xml", "<p:presentation/>"));
	}

	// 매크로가 든 PowerPoint (확장자만 pptx로 바꾼 경우)
	public static byte[] pptm() {
		return zip(Map.of(
				"[Content_Types].xml", contentTypes(PPTM_MAIN, "/ppt/presentation.xml"),
				"ppt/presentation.xml", "<p:presentation/>"));
	}

	// Word 문서 (확장자만 pptx로 바꾼 경우)
	public static byte[] docx() {
		return zip(Map.of(
				"[Content_Types].xml", contentTypes(DOCX_MAIN, "/word/document.xml"),
				"word/document.xml", "<w:document/>"));
	}

	// 아무 ZIP (확장자만 pptx로 바꾼 경우)
	public static byte[] plainZip() {
		return zip(Map.of("note.txt", "hello"));
	}

	public static byte[] utf8(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	public static byte[] eucKr(String text) {
		return text.getBytes(Charset.forName("EUC-KR"));
	}

	private static String contentTypes(String mainType, String partName) {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types>"
				+ "<Override PartName=\"" + partName + "\" ContentType=\"" + mainType + "\"/></Types>";
	}

	private static byte[] zip(Map<String, String> entries) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (var entry : entries.entrySet()) {
				zip.putNextEntry(new ZipEntry(entry.getKey()));
				zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return bytes.toByteArray();
	}
}
