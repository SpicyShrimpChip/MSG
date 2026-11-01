package com.ailms.backend.domain.material.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 올릴 수 있는 자료 형식. 프론트 constants/materials.ts의 MATERIAL_EXTENSIONS와 같은 목록이다.
 * TODO(미결): HWP·HWPX는 텍스트 추출 라이브러리(hwplib·hwpxlib)를 정한 뒤 추가한다.
 */
public enum MaterialFileFormat {

	PDF("pdf", "application/pdf"),
	PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
	TXT("txt", "text/plain"),
	MD("md", "text/markdown");

	// 형식이 맞지 않을 때 보여 줄 안내 (프론트 utils/material.ts의 MATERIAL_FORMAT_LABEL과 같은 순서·표기)
	public static final String LABEL = "PDF, PPTX, TXT, Markdown";

	private final String extension;
	private final String contentType;

	MaterialFileFormat(String extension, String contentType) {
		this.extension = extension;
		this.contentType = contentType;
	}

	public String extension() {
		return extension;
	}

	public String contentType() {
		return contentType;
	}

	public boolean isText() {
		return this == TXT || this == MD;
	}

	public static Optional<MaterialFileFormat> fromExtension(String extension) {
		return Arrays.stream(values()).filter(format -> format.extension.equals(extension)).findFirst();
	}
}
