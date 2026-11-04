package com.ailms.backend.domain.quiz.application;

import com.ailms.backend.domain.material.entity.Material;
import com.ailms.backend.domain.material.entity.MaterialFileFormat;
import com.ailms.backend.global.storage.FileStorage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * 학습자료 원본에서 글을 뽑는다. 서버 안에서만 읽고 외부로 보내지 않는다(보내는지는 부르는 쪽이 정책·동의를 보고 정한다).
 * <ul>
 * <li>TXT·MD: UTF-8 글 그대로 (업로드 때 UTF-8·NUL 검사를 이미 통과했다)</li>
 * <li>PPTX: ZIP 안의 슬라이드 XML(ppt/slides/slideN.xml)에서 글 칸(&lt;a:t&gt;)만 슬라이드 순서대로, "[슬라이드 N]" 표시.
 * 압축을 푼 크기를 세어 상한을 넘으면 멈춘다(압축 폭탄 방지)</li>
 * <li>PDF: Apache PDFBox로 쪽마다 글을 뽑고 "[p. N]" 표시. 쪽 수는 maxPages까지. 암호가 걸린 PDF는 ENCRYPTED,
 * 글이 없는 PDF(스캔본 등, OCR 필요)는 NO_TEXT, 손상된 파일은 FAILED</li>
 * </ul>
 * 결과는 maxChars까지만 돌려주고 저장하지 않는다. 본문은 로그에 남기지 않는다.
 */
@Component
@RequiredArgsConstructor
public class MaterialTextExtractor {

	// 슬라이드 XML 하나를 이만큼까지만 읽는다 (압축 폭탄 방지)
	static final long MAX_SLIDE_BYTES = 2L * 1024 * 1024;
	static final long MAX_TOTAL_BYTES = 20L * 1024 * 1024;
	private static final Pattern SLIDE = Pattern.compile("^ppt/slides/slide(\\d+)\\.xml$");
	private static final Pattern TEXT_RUN = Pattern.compile("<a:t>([^<]*)</a:t>|</a:p>");

	private final FileStorage fileStorage;
	private final MaterialContentProperties properties;

	public enum Status {
		/** 글을 뽑았다 */
		OK,
		/** 읽을 글이 없다 (TXT·MD·PPTX) */
		EMPTY,
		/** 글이 없는 PDF (스캔본일 수 있음, OCR 미지원) */
		NO_TEXT,
		/** 암호가 걸린 PDF */
		ENCRYPTED,
		/** 지원하지 않는 형식 */
		NOT_SUPPORTED,
		/** 파일이 손상됐거나 읽지 못했다 */
		FAILED
	}

	public record Extracted(Status status, String text, boolean truncated, int pagesRead, int pagesTotal) {

		static Extracted of(Status status) {
			return new Extracted(status, null, false, 0, 0);
		}
	}

	public Extracted extract(Material material) {
		return extract(material, properties.maxChars());
	}

	public Extracted extract(Material material, int maxChars) {
		MaterialFileFormat format = material.getFileFormat();
		try (InputStream in = fileStorage.open(material.getStorageKey())) {
			int[] pages = {0, 0};
			String text = switch (format) {
				case PDF -> pdf(in, pages);
				case PPTX -> pptx(in);
				case TXT, MD -> utf8(in, maxChars);
			};
			if (text == null) {
				return Extracted.of(Status.ENCRYPTED);
			}
			text = text.replace("\r\n", "\n").replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").strip();
			if (text.isEmpty() || (format == MaterialFileFormat.PDF && text.replaceAll("\\[p\\. \\d+]", "").isBlank())) {
				return new Extracted(format == MaterialFileFormat.PDF ? Status.NO_TEXT : Status.EMPTY, null, false,
						pages[0], pages[1]);
			}
			boolean truncated = text.length() > maxChars || pages[0] < pages[1];
			return new Extracted(Status.OK, text.length() > maxChars ? text.substring(0, maxChars) : text, truncated,
					pages[0], pages[1]);
		} catch (IOException | RuntimeException e) {
			return Extracted.of(Status.FAILED);
		}
	}

	private static String utf8(InputStream in, int maxChars) throws IOException {
		// 글자 수 상한의 4배(UTF-8 최대 바이트)까지만 읽는다
		byte[] bytes = in.readNBytes(Math.max(1, maxChars) * 4 + 4);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	// 암호가 걸렸으면 null. pages[0] = 읽은 쪽 수, pages[1] = 전체 쪽 수
	private String pdf(InputStream in, int[] pages) throws IOException {
		byte[] bytes = in.readAllBytes();
		try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(bytes))) {
			if (document.isEncrypted() && !document.getCurrentAccessPermission().canExtractContent()) {
				return null;
			}
			int total = document.getNumberOfPages();
			int read = Math.min(total, properties.maxPdfPages());
			pages[0] = read;
			pages[1] = total;
			PDFTextStripper stripper = new PDFTextStripper();
			StringBuilder text = new StringBuilder();
			for (int page = 1; page <= read; page++) {
				stripper.setStartPage(page);
				stripper.setEndPage(page);
				String body = stripper.getText(document).strip();
				if (!body.isEmpty()) {
					text.append("[p. ").append(page).append("]\n").append(body).append("\n\n");
				}
				if (text.length() > properties.maxChars() * 2) {
					break;
				}
			}
			return text.toString();
		} catch (InvalidPasswordException e) {
			return null;
		}
	}

	private static String pptx(InputStream in) throws IOException {
		Map<Integer, String> slides = new TreeMap<>();
		long total = 0;
		try (ZipInputStream zip = new ZipInputStream(in)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				Matcher name = SLIDE.matcher(entry.getName());
				if (!name.matches()) {
					continue;
				}
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buffer = new byte[8192];
				long read = 0;
				int n;
				while ((n = zip.read(buffer)) > 0) {
					read += n;
					total += n;
					if (read > MAX_SLIDE_BYTES || total > MAX_TOTAL_BYTES) {
						throw new IOException("슬라이드가 너무 크다");
					}
					out.write(buffer, 0, n);
				}
				slides.put(Integer.parseInt(name.group(1)), slideText(out.toString(StandardCharsets.UTF_8)));
			}
		}
		StringBuilder text = new StringBuilder();
		slides.forEach((number, body) -> {
			if (!body.isBlank()) {
				text.append("[슬라이드 ").append(number).append("]\n").append(body.strip()).append("\n\n");
			}
		});
		return text.toString();
	}

	private static String slideText(String xml) {
		StringBuilder text = new StringBuilder();
		Matcher matcher = TEXT_RUN.matcher(xml);
		while (matcher.find()) {
			if (matcher.group(1) == null) {
				text.append('\n');
			} else {
				text.append(unescape(matcher.group(1)));
			}
		}
		return text.toString();
	}

	private static String unescape(String text) {
		return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
				.replace("&amp;", "&");
	}
}
