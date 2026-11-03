package com.ailms.backend.domain.quiz.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ailms.backend.domain.material.entity.Material;
import com.ailms.backend.domain.material.entity.MaterialFileFormat;
import com.ailms.backend.global.storage.FileStorage;
import com.ailms.backend.support.TestFiles;
import com.ailms.backend.support.TestPdfs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

// 자료 본문 추출: 서버 안에서만 읽는다 (외부 전송 없음)
class MaterialTextExtractorTest {

	private final Map<String, byte[]> files = new HashMap<>();
	private final FileStorage storage = new FileStorage() {
		@Override
		public void store(String key, Path source) {
		}

		@Override
		public InputStream open(String key) {
			return new ByteArrayInputStream(files.get(key));
		}

		@Override
		public boolean exists(String key) {
			return files.containsKey(key);
		}

		@Override
		public void delete(String key) {
		}
	};
	private final MaterialTextExtractor extractor = new MaterialTextExtractor(storage,
			new MaterialContentProperties(true, 12_000, 2));

	@Test
	void 텍스트_PDF는_쪽마다_표시를_붙여_읽는다() {
		var result = extractor.extract(material(MaterialFileFormat.PDF,
				TestPdfs.withPages("NAT translates private addresses", "PAT uses ports")));

		assertThat(result.status()).isEqualTo(MaterialTextExtractor.Status.OK);
		assertThat(result.text()).contains("[p. 1]", "NAT translates private addresses", "[p. 2]", "PAT uses ports");
		assertThat(result.pagesRead()).isEqualTo(2);
	}

	@Test
	void PDF는_정해진_쪽_수까지만_읽고_잘렸다고_표시한다() {
		var result = extractor.extract(material(MaterialFileFormat.PDF, TestPdfs.withPages("one", "two", "three")));

		assertThat(result.text()).contains("[p. 2]").doesNotContain("three");
		assertThat(result.truncated()).isTrue();
		assertThat(result.pagesTotal()).isEqualTo(3);
	}

	@Test
	void 글이_없는_PDF_암호_PDF_손상된_PDF를_구분한다() {
		assertThat(extractor.extract(material(MaterialFileFormat.PDF, TestPdfs.withPages((String) null))).status())
				.isEqualTo(MaterialTextExtractor.Status.NO_TEXT);
		assertThat(extractor.extract(material(MaterialFileFormat.PDF, TestPdfs.encrypted("hidden"))).status())
				.isEqualTo(MaterialTextExtractor.Status.ENCRYPTED);
		assertThat(extractor.extract(material(MaterialFileFormat.PDF, "%PDF-1.4 broken %%EOF".getBytes())).status())
				.isIn(MaterialTextExtractor.Status.FAILED, MaterialTextExtractor.Status.NO_TEXT);
	}

	@Test
	void PPTX와_TXT도_읽고_글자_수를_자른다() {
		var slides = extractor.extract(material(MaterialFileFormat.PPTX,
				TestFiles.pptxWithSlides("VLAN 나누기", "트렁크 &amp; 태그")));
		assertThat(slides.text()).contains("[슬라이드 1]", "VLAN 나누기", "[슬라이드 2]", "트렁크 & 태그");

		var text = new MaterialTextExtractor(storage, new MaterialContentProperties(true, 5, 10))
				.extract(material(MaterialFileFormat.TXT, "가나다라마바사".getBytes(StandardCharsets.UTF_8)));
		assertThat(text.text()).isEqualTo("가나다라마");
		assertThat(text.truncated()).isTrue();
	}

	private Material material(MaterialFileFormat format, byte[] bytes) {
		String key = "k" + files.size();
		files.put(key, bytes);
		return Material.upload(1L, 1L, "자료", "file." + format.name().toLowerCase(), format, bytes.length, key,
				LocalDateTime.now());
	}
}
