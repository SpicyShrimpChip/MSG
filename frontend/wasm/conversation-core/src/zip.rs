//! zip 항목 하나의 압축 풀기와 크기·CRC 확인. 파일을 읽는 일(Blob.slice)은 JS가 하고, 여기서는 받은 바이트만 다룬다.

const STORED: u16 = 0;
const DEFLATED: u16 = 8;

#[derive(Debug, PartialEq)]
pub enum ZipError {
    /// 압축이 풀리지 않는다
    Inflate,
    /// 지원하지 않는 압축 방식
    Method,
    /// 푼 크기나 CRC가 목차와 다르다
    Corrupt,
}

impl ZipError {
    /// JS가 사용자 문구로 바꾸는 이유 코드
    pub fn code(&self) -> &'static str {
        match self {
            ZipError::Inflate => "INFLATE",
            ZipError::Method => "METHOD",
            ZipError::Corrupt => "CORRUPT",
        }
    }
}

pub fn decompress(
    compressed: &[u8],
    method: u16,
    uncompressed_size: f64,
    crc: u32,
) -> Result<Vec<u8>, ZipError> {
    if !uncompressed_size.is_finite() || uncompressed_size < 0.0 {
        return Err(ZipError::Corrupt);
    }
    let data = match method {
        STORED => compressed.to_vec(),
        DEFLATED => {
            // 목차에 적힌 크기보다 크게 풀리면 손상(또는 압축 폭탄)으로 보고 멈춘다
            let limit = uncompressed_size as usize;
            miniz_oxide::inflate::decompress_to_vec_with_limit(compressed, limit.max(1))
                .map_err(|_| ZipError::Inflate)?
        }
        _ => return Err(ZipError::Method),
    };
    if data.len() as f64 != uncompressed_size || crc32fast::hash(&data) != crc {
        return Err(ZipError::Corrupt);
    }
    Ok(data)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 압축을_풀고_crc를_확인한다() {
        let text = "안녕하세요 conversations".repeat(50);
        let compressed = miniz_oxide::deflate::compress_to_vec(text.as_bytes(), 6);
        let crc = crc32fast::hash(text.as_bytes());
        let size = text.len() as f64;
        assert_eq!(decompress(&compressed, DEFLATED, size, crc).unwrap(), text.as_bytes());
        assert_eq!(decompress(&compressed, DEFLATED, size, crc ^ 1), Err(ZipError::Corrupt));
        assert_eq!(decompress(&compressed, 99, size, crc), Err(ZipError::Method));
        assert_eq!(decompress(&[1, 2, 3], DEFLATED, 10.0, 0), Err(ZipError::Inflate));
    }

    #[test]
    fn 목차보다_크게_풀리면_멈춘다() {
        let data = vec![0u8; 10_000];
        let compressed = miniz_oxide::deflate::compress_to_vec(&data, 6);
        assert!(decompress(&compressed, DEFLATED, 100.0, crc32fast::hash(&data)).is_err());
    }
}
