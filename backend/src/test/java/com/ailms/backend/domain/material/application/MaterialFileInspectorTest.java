package com.ailms.backend.domain.material.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ailms.backend.domain.material.application.MaterialFileInspector.InspectedFile;
import com.ailms.backend.domain.material.entity.MaterialFileFormat;
import com.ailms.backend.global.error.BusinessException;
import com.ailms.backend.support.TestFiles;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

// 확장자와 실제 내용이 함께 맞아야 통과하는지 확인한다
class MaterialFileInspectorTest {

	private final MaterialFileInspector inspector = new MaterialFileInspector();

	@TempDir
	Path temp;

	@Test
	void 네_가지_형식을_내용까지_확인하고_통과시킨다() throws IOException {
		assertThat(inspect("노트.pdf", TestFiles.pdf()).format()).isEqualTo(MaterialFileFormat.PDF);
		assertThat(inspect("발표.PPTX", TestFiles.pptx()).format()).isEqualTo(MaterialFileFormat.PPTX);
		assertThat(inspect("필기.txt", TestFiles.utf8("페이징 정리\n")).format()).isEqualTo(MaterialFileFormat.TXT);
		assertThat(inspect("정리.md", TestFiles.utf8("\uFEFF# 해시\n- 솔트")).format()).isEqualTo(MaterialFileFormat.MD);
	}

	@Test
	void 크기와_정리한_파일_이름을_돌려준다() throws IOException {
		String nfd = Normalizer.normalize("정리노트.pdf", Normalizer.Form.NFD);
		InspectedFile file = inspect("C:\\Users\\me\\" + nfd, TestFiles.pdf());

		assertThat(file.fileName()).isEqualTo("정리노트.pdf");
		assertThat(file.size()).isEqualTo(TestFiles.pdf().length);
	}

	@Test
	void 지원하지_않는_확장자는_거절한다() {
		assertRejected("과제.hwp", TestFiles.utf8("x"), HttpStatus.BAD_REQUEST, "PDF, PPTX, TXT, Markdown 파일만 올릴 수 있어요.");
		assertRejected("실행.exe", TestFiles.utf8("x"), HttpStatus.BAD_REQUEST, "PDF, PPTX, TXT, Markdown 파일만 올릴 수 있어요.");
		assertRejected("확장자없음", TestFiles.utf8("x"), HttpStatus.BAD_REQUEST, "PDF, PPTX, TXT, Markdown 파일만 올릴 수 있어요.");
		assertRejected("노트.pdf.exe", TestFiles.pdf(), HttpStatus.BAD_REQUEST, "PDF, PPTX, TXT, Markdown 파일만 올릴 수 있어요.");
	}

	@Test
	void 빈_파일과_20MB를_넘는_파일은_거절한다() throws IOException {
		assertRejected("빈.txt", new byte[0], HttpStatus.BAD_REQUEST, "빈 파일은 올릴 수 없어요.");

		Path big = temp.resolve("big.txt");
		try (RandomAccessFile file = new RandomAccessFile(big.toFile(), "rw")) {
			file.setLength(20L * 1024 * 1024 + 1);
		}
		assertThatThrownBy(() -> inspector.inspect("큰.txt", big))
				.isInstanceOfSatisfying(BusinessException.class, e -> {
					assertThat(e.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
					assertThat(e.getMessage()).isEqualTo("파일은 20MB까지 올릴 수 있어요.");
				});
	}

	@Test
	void 확장자만_PDF인_파일은_거절한다() {
		String message = "PDF 파일이 아니거나 손상된 파일이에요. 파일을 확인해 주세요.";
		assertRejected("가짜.pdf", TestFiles.utf8("그냥 글자"), HttpStatus.BAD_REQUEST, message);
		assertRejected("가짜.pdf", TestFiles.pptx(), HttpStatus.BAD_REQUEST, message);
		// 끝이 잘린 PDF
		assertRejected("잘림.pdf", TestFiles.utf8("%PDF-1.4\n1 0 obj"), HttpStatus.BAD_REQUEST, message);
	}

	@Test
	void PPTX는_ZIP이라도_프레젠테이션이_아니면_거절한다() {
		String message = "PPTX 파일이 아니거나 손상된 파일이에요. 암호를 건 파일은 올릴 수 없어요.";
		assertRejected("압축.pptx", TestFiles.plainZip(), HttpStatus.BAD_REQUEST, message);
		assertRejected("워드.pptx", TestFiles.docx(), HttpStatus.BAD_REQUEST, message);
		assertRejected("매크로.pptx", TestFiles.pptm(), HttpStatus.BAD_REQUEST, message);
		assertRejected("가짜.pptx", TestFiles.pdf(), HttpStatus.BAD_REQUEST, message);
		// ZIP 머리글만 있고 구조가 깨진 파일
		assertRejected("깨짐.pptx", new byte[] {'P', 'K', 3, 4, 0, 0, 0}, HttpStatus.BAD_REQUEST, message);
	}

	@Test
	void 글자_파일은_UTF8이_아니거나_바이너리면_거절한다() {
		String message = "텍스트 파일은 UTF-8로 저장한 글자 파일만 올릴 수 있어요.";
		assertRejected("옛날.txt", TestFiles.eucKr("한글 필기"), HttpStatus.BAD_REQUEST, message);
		assertRejected("바이너리.md", new byte[] {'a', 0, 'b'}, HttpStatus.BAD_REQUEST, message);
		assertRejected("압축.txt", TestFiles.pptx(), HttpStatus.BAD_REQUEST, message);
	}

	@Test
	void 비어_있거나_너무_긴_파일_이름은_거절한다() {
		assertRejected(null, TestFiles.pdf(), HttpStatus.BAD_REQUEST, "파일 이름을 확인해 주세요.");
		assertRejected("dir/  ", TestFiles.pdf(), HttpStatus.BAD_REQUEST, "파일 이름을 확인해 주세요.");
		assertRejected("가".repeat(252) + ".pdf", TestFiles.pdf(), HttpStatus.BAD_REQUEST, "파일 이름은 255자까지 올릴 수 있어요.");
	}

	private InspectedFile inspect(String name, byte[] content) throws IOException {
		return inspector.inspect(name, write(content));
	}

	private void assertRejected(String name, byte[] content, HttpStatus status, String message) {
		assertThatThrownBy(() -> inspect(name, content))
				.isInstanceOfSatisfying(BusinessException.class, e -> {
					assertThat(e.getStatus()).isEqualTo(status);
					assertThat(e.getMessage()).isEqualTo(message);
				});
	}

	private Path write(byte[] content) throws IOException {
		return Files.write(Files.createTempFile(temp, "upload-", ".bin"), content);
	}
}
