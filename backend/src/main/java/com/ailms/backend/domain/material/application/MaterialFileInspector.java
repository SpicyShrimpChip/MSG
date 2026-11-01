package com.ailms.backend.domain.material.application;

import com.ailms.backend.domain.material.entity.Material;
import com.ailms.backend.domain.material.entity.MaterialFileFormat;
import com.ailms.backend.global.error.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 올린 파일을 저장하기 전에 검사한다. 확장자만 믿지 않고 파일 내용이 그 형식인지 확인한다.
 *
 * <ul>
 * <li>PDF: 앞쪽에 %PDF- 머리글, 끝쪽에 %%EOF가 있어야 한다.</li>
 * <li>PPTX: ZIP이면서 PowerPoint 프레젠테이션의 필수 항목([Content_Types].xml의 프레젠테이션 형식,
 * ppt/presentation.xml)이 있어야 한다. 다른 ZIP·DOCX·XLSX·매크로 포함 PPTM은 거절한다.
 * 압축은 풀지 않고 목록과 작은 형식 파일만 읽는다(압축 폭탄 방지).</li>
 * <li>TXT·MD: UTF-8로 끝까지 읽혀야 하고 NUL 문자가 없어야 한다(실행 파일 등 바이너리 거절).</li>
 * </ul>
 */
@Component
public class MaterialFileInspector {

	static final String FORMAT_MESSAGE = MaterialFileFormat.LABEL + " 파일만 올릴 수 있어요.";
	static final String EMPTY_MESSAGE = "빈 파일은 올릴 수 없어요.";
	static final String TOO_LARGE_MESSAGE = "파일은 20MB까지 올릴 수 있어요.";
	static final String FILE_NAME_MESSAGE = "파일 이름을 확인해 주세요.";
	static final String FILE_NAME_TOO_LONG_MESSAGE = "파일 이름은 255자까지 올릴 수 있어요.";
	static final String PDF_MESSAGE = "PDF 파일이 아니거나 손상된 파일이에요. 파일을 확인해 주세요.";
	static final String PPTX_MESSAGE = "PPTX 파일이 아니거나 손상된 파일이에요. 암호를 건 파일은 올릴 수 없어요.";
	static final String TEXT_MESSAGE = "텍스트 파일은 UTF-8로 저장한 글자 파일만 올릴 수 있어요.";

	private static final int PDF_HEADER_WINDOW = 1024;
	private static final int PDF_TRAILER_WINDOW = 1024;
	private static final int ZIP_MAX_ENTRIES = 10_000;
	private static final int CONTENT_TYPES_MAX_BYTES = 1024 * 1024;
	private static final String PPTX_MAIN_CONTENT_TYPE =
			"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml";

	/**
	 * @param originalFileName 브라우저가 보낸 파일 이름 (경로가 붙어 올 수도 있다)
	 * @param content          받은 파일 내용을 담은 임시 파일
	 */
	public InspectedFile inspect(String originalFileName, Path content) {
		String fileName = displayName(originalFileName);
		MaterialFileFormat format = MaterialFileFormat.fromExtension(extension(fileName))
				.orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, FORMAT_MESSAGE));
		long size = size(content);
		if (size == 0) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, EMPTY_MESSAGE);
		}
		if (size > Material.MAX_FILE_SIZE) {
			throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, TOO_LARGE_MESSAGE);
		}
		switch (format) {
			case PDF -> checkPdf(content, size);
			case PPTX -> checkPptx(content);
			case TXT, MD -> checkText(content);
		}
		return new InspectedFile(fileName, format, size);
	}

	public record InspectedFile(String fileName, MaterialFileFormat format, long size) {
	}

	// 화면에 보여 줄 이름: 경로 부분과 제어 문자를 떼고, macOS가 보내는 풀어 쓴 한글(NFD)을 붙여 쓴다(NFC)
	static String displayName(String raw) {
		if (raw == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, FILE_NAME_MESSAGE);
		}
		String name = raw.substring(Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\')) + 1);
		name = Normalizer.normalize(name, Normalizer.Form.NFC).replaceAll("\\p{Cntrl}", "").strip();
		if (name.isEmpty()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, FILE_NAME_MESSAGE);
		}
		if (name.length() > Material.FILE_NAME_MAX_LENGTH) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, FILE_NAME_TOO_LONG_MESSAGE);
		}
		return name;
	}

	private static String extension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static long size(Path content) {
		try {
			return Files.size(content);
		} catch (IOException e) {
			throw new IllegalStateException("받은 파일의 크기를 읽지 못했다", e);
		}
	}

	private static void checkPdf(Path content, long size) {
		try (SeekableByteChannel file = Files.newByteChannel(content)) {
			byte[] head = read(file, 0, (int) Math.min(PDF_HEADER_WINDOW, size));
			long tailStart = Math.max(0, size - PDF_TRAILER_WINDOW);
			byte[] tail = read(file, tailStart, (int) (size - tailStart));
			if (indexOf(head, "%PDF-".getBytes(StandardCharsets.US_ASCII)) < 0
					|| indexOf(tail, "%%EOF".getBytes(StandardCharsets.US_ASCII)) < 0) {
				throw new BusinessException(HttpStatus.BAD_REQUEST, PDF_MESSAGE);
			}
		} catch (IOException e) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, PDF_MESSAGE);
		}
	}

	private static void checkPptx(Path content) {
		try (InputStream in = Files.newInputStream(content)) {
			byte[] signature = in.readNBytes(4);
			if (!Arrays.equals(signature, new byte[] {'P', 'K', 3, 4})) {
				throw new BusinessException(HttpStatus.BAD_REQUEST, PPTX_MESSAGE);
			}
		} catch (IOException e) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, PPTX_MESSAGE);
		}
		// ZipFile은 파일 끝의 목록(central directory)만 읽고, 항목은 열 때만 푼다
		try (ZipFile zip = new ZipFile(content.toFile(), StandardCharsets.UTF_8)) {
			if (zip.size() > ZIP_MAX_ENTRIES || zip.getEntry("ppt/presentation.xml") == null) {
				throw new BusinessException(HttpStatus.BAD_REQUEST, PPTX_MESSAGE);
			}
			ZipEntry contentTypes = zip.getEntry("[Content_Types].xml");
			if (contentTypes == null) {
				throw new BusinessException(HttpStatus.BAD_REQUEST, PPTX_MESSAGE);
			}
			byte[] xml;
			try (InputStream in = zip.getInputStream(contentTypes)) {
				// 선언한 크기를 믿지 않고 한도까지만 푼다
				xml = in.readNBytes(CONTENT_TYPES_MAX_BYTES + 1);
			}
			if (xml.length > CONTENT_TYPES_MAX_BYTES
					|| !new String(xml, StandardCharsets.UTF_8).contains(PPTX_MAIN_CONTENT_TYPE)) {
				throw new BusinessException(HttpStatus.BAD_REQUEST, PPTX_MESSAGE);
			}
		} catch (IOException e) {
			// ZIP 구조가 깨졌을 때(ZipException)도 여기로 온다
			throw new BusinessException(HttpStatus.BAD_REQUEST, PPTX_MESSAGE);
		}
	}

	private static void checkText(Path content) {
		var decoder = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
		char[] buffer = new char[8192];
		try (Reader reader = new InputStreamReader(Files.newInputStream(content), decoder)) {
			int read;
			while ((read = reader.read(buffer)) != -1) {
				for (int i = 0; i < read; i++) {
					if (buffer[i] == '\0') {
						throw new BusinessException(HttpStatus.BAD_REQUEST, TEXT_MESSAGE);
					}
				}
			}
		} catch (CharacterCodingException e) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, TEXT_MESSAGE);
		} catch (IOException e) {
			throw new IllegalStateException("받은 파일을 읽지 못했다", e);
		}
	}

	private static byte[] read(SeekableByteChannel channel, long position, int length)
			throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(length);
		channel.position(position);
		while (buffer.hasRemaining() && channel.read(buffer) > 0) {
			// 다 읽을 때까지 반복
		}
		return Arrays.copyOf(buffer.array(), buffer.position());
	}

	private static int indexOf(byte[] data, byte[] pattern) {
		outer:
		for (int i = 0; i <= data.length - pattern.length; i++) {
			for (int j = 0; j < pattern.length; j++) {
				if (data[i + j] != pattern[j]) {
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}
}
