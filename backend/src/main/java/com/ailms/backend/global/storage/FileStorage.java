package com.ailms.backend.global.storage;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * 업로드 원본 같은 파일을 저장하는 곳. 지금은 로컬 디스크(LocalFileStorage)이고, S3 같은 다른 저장소는
 * 이 인터페이스의 구현을 추가하고 app.storage.type으로 고른다.
 * TODO(미결): AWS 세부 서비스(S3 등)는 정해지지 않았다.
 *
 * <p>key는 서버가 만든 값만 쓴다(사용자가 보낸 파일 이름을 key에 넣지 않는다).
 * 영문·숫자·'_'·'-'·'.'과 '/' 구분만 허용하고, 각 구간은 영문·숫자로 시작해야 한다 (예: materials/3/0b9f….pdf).
 * 파일 정보(이름·형식·크기·소유자)는 DB(material 등)에 두고, 저장소에는 파일 내용만 둔다.
 */
public interface FileStorage {

	/** source 파일 내용을 key 위치에 저장한다. 이미 같은 key가 있으면 덮어쓰지 않고 실패한다. */
	void store(String key, Path source);

	/** 저장한 파일을 읽는다. 다 읽은 뒤 스트림을 닫아야 한다. 없으면 FileStorageException. */
	InputStream open(String key);

	boolean exists(String key);

	/** 파일을 지운다. 이미 없으면 아무것도 하지 않는다(여러 번 불러도 결과가 같다). */
	void delete(String key);
}
