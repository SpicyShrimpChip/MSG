package com.ailms.backend.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * 테스트용 PDF를 메모리에서 만든다 (직접 쓴 짧은 영문 글, 실제 강의자료 아님).
 */
public final class TestPdfs {

	private TestPdfs() {
	}

	// 쪽마다 글 한 줄 (null이면 글 없는 쪽: 스캔본처럼)
	public static byte[] withPages(String... pages) {
		return build(null, pages);
	}

	// 열 때 암호가 필요한 PDF
	public static byte[] encrypted(String text) {
		return build("secret", text);
	}

	private static byte[] build(String password, String... pages) {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			for (String text : pages) {
				PDPage page = new PDPage();
				document.addPage(page);
				if (text != null) {
					try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
						stream.beginText();
						stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
						stream.newLineAtOffset(72, 700);
						stream.showText(text);
						stream.endText();
					}
				}
			}
			if (password != null) {
				StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", password, new AccessPermission());
				policy.setEncryptionKeyLength(128);
				document.protect(policy);
			}
			document.save(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
