//! 대화 가져오기의 무거운 처리를 WebAssembly로 돌린다.
//! - `mask`: 개인정보 가리기 (frontend `utils/conversation/mask.ts`의 규칙과 같다)
//! - `zip`: zip 항목 압축 풀기와 크기·CRC 확인 (`utils/conversation/zipReader.ts`가 부른다)
//!
//! 글자 위치는 JS 문자열과 맞추려고 UTF-16 단위로 돌려준다.

mod js;
pub mod mask;
pub mod zip;

use wasm_bindgen::prelude::*;

/// 글 여러 개를 같은 단어로 가린다. 결과는 `[{ text, matches: [{ kind, original, start, end }] }]` JSON.
/// 가릴 단어 정규식은 한 번만 만든다(대화 하나의 제목·메시지를 한꺼번에 넘긴다).
#[wasm_bindgen]
pub fn mask_texts(texts: Vec<String>, names: Vec<String>, terms: Vec<String>) -> String {
    let masker = mask::Masker::new(&names, &terms);
    let results: Vec<mask::MaskResult> = texts.iter().map(|text| masker.mask(text)).collect();
    serde_json::to_string(&results).unwrap_or_else(|_| "[]".to_string())
}

/// zip 항목 하나의 압축을 풀고 크기·CRC를 확인한다. 실패하면 이유 코드를 담은 오류를 던진다.
#[wasm_bindgen]
pub fn inflate_entry(
    compressed: &[u8],
    method: u16,
    uncompressed_size: f64,
    crc: u32,
) -> Result<Vec<u8>, JsError> {
    zip::decompress(compressed, method, uncompressed_size, crc)
        .map_err(|error| JsError::new(error.code()))
}

/// CRC-32 (zip과 같은 방식)
#[wasm_bindgen]
pub fn crc32(data: &[u8]) -> u32 {
    crc32fast::hash(data)
}
