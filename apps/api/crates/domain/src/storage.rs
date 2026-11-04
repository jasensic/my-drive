use bytes::Bytes;
use futures_util::stream::{self, BoxStream};

use crate::DomainError;

/// Chunks of an object, in order. Nothing holds the whole object at once.
pub type ByteStream = BoxStream<'static, Result<Bytes, DomainError>>;

/// An inclusive byte range, as in an HTTP `Range: bytes=start-end` header.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ByteRange {
    pub start: u64,
    pub end: Option<u64>,
}

impl ByteRange {
    /// The range clamped to an object of `total` bytes. None when it starts past the end.
    pub fn resolve(self, total: u64) -> Option<(u64, u64)> {
        let last = total.checked_sub(1)?;
        if self.start > last {
            return None;
        }
        Some((self.start, self.end.map_or(last, |end| end.min(last))))
    }
}

/// The bytes of an object, or of a range of it, plus where they sit in the whole.
pub struct ObjectBody {
    /// Size of the whole object.
    pub total: u64,
    /// Offset of the first byte of `stream` within the object.
    pub start: u64,
    /// Number of bytes `stream` will yield.
    pub len: u64,
    pub stream: ByteStream,
}

impl ObjectBody {
    pub fn from_bytes(bytes: Bytes) -> Self {
        let len = bytes.len() as u64;
        Self {
            total: len,
            start: 0,
            len,
            stream: Box::pin(stream::once(async move { Ok(bytes) })),
        }
    }

    /// The part of an object already in memory that `range` selects, for stores that keep it there.
    pub fn from_slice(data: Bytes, range: Option<ByteRange>) -> Result<Self, DomainError> {
        let total = data.len() as u64;
        let Some(range) = range else {
            return Ok(Self::from_bytes(data));
        };
        let (start, end) = range
            .resolve(total)
            .ok_or(DomainError::RangeNotSatisfiable { total })?;
        let part = data.slice(start as usize..=end as usize);
        Ok(Self {
            total,
            start,
            len: part.len() as u64,
            stream: Box::pin(stream::once(async move { Ok(part) })),
        })
    }

    /// Reads everything. For small objects and tests; large ones should be streamed.
    pub async fn collect(self) -> Result<Bytes, DomainError> {
        collect_stream(self.stream).await
    }
}

/// Gathers a stream into one buffer, failing with the first error it yields.
pub async fn collect_stream(mut stream: ByteStream) -> Result<Bytes, DomainError> {
    use futures_util::StreamExt;
    let mut out = Vec::new();
    while let Some(chunk) = stream.next().await {
        out.extend_from_slice(&chunk?);
    }
    Ok(Bytes::from(out))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resolve_clamps_the_end_and_rejects_a_start_past_the_object() {
        let open = ByteRange { start: 10, end: None };
        assert_eq!(open.resolve(100), Some((10, 99)));
        assert_eq!(ByteRange { start: 10, end: Some(500) }.resolve(100), Some((10, 99)));
        assert_eq!(ByteRange { start: 10, end: Some(20) }.resolve(100), Some((10, 20)));
        assert_eq!(ByteRange { start: 100, end: None }.resolve(100), None);
        assert_eq!(ByteRange { start: 0, end: None }.resolve(0), None);
    }

    #[tokio::test]
    async fn from_slice_returns_the_selected_range_and_its_place_in_the_whole() {
        let data = Bytes::from_static(b"0123456789");
        let body = ObjectBody::from_slice(data.clone(), Some(ByteRange { start: 3, end: Some(5) })).unwrap();
        assert_eq!((body.total, body.start, body.len), (10, 3, 3));
        assert_eq!(body.collect().await.unwrap(), Bytes::from_static(b"345"));

        let tail = ObjectBody::from_slice(data.clone(), Some(ByteRange { start: 7, end: None })).unwrap();
        assert_eq!(tail.collect().await.unwrap(), Bytes::from_static(b"789"));

        let whole = ObjectBody::from_slice(data, None).unwrap();
        assert_eq!((whole.total, whole.start, whole.len), (10, 0, 10));
    }

    #[test]
    fn from_slice_past_the_end_reports_the_object_size() {
        let data = Bytes::from_static(b"0123456789");
        let err = ObjectBody::from_slice(data, Some(ByteRange { start: 10, end: None })).err().unwrap();
        assert!(matches!(err, DomainError::RangeNotSatisfiable { total: 10 }));
    }
}
