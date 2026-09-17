use std::io::{Cursor, Read, Write};

use domain::apk::{parse_android_manifest, ApkIdentity};
use domain::ports::ApkInspector;
use domain::DomainError;
use zip::write::SimpleFileOptions;
use zip::{ZipArchive, ZipWriter};

pub struct ZipApkInspector;

impl ApkInspector for ZipApkInspector {
    fn inspect(&self, apk: &[u8]) -> Result<ApkIdentity, DomainError> {
        inspect_apk(apk)
    }
}

pub fn inspect_apk(apk: &[u8]) -> Result<ApkIdentity, DomainError> {
    let mut archive = ZipArchive::new(Cursor::new(apk))
        .map_err(|_| DomainError::validation("file is not a valid APK/zip"))?;
    let mut manifest = archive
        .by_name("AndroidManifest.xml")
        .map_err(|_| DomainError::validation("APK is missing AndroidManifest.xml"))?;
    let mut bytes = Vec::new();
    manifest
        .read_to_end(&mut bytes)
        .map_err(|e| DomainError::validation(format!("cannot read AndroidManifest.xml: {e}")))?;
    parse_android_manifest(&bytes)
}

pub fn package_apk(version_code: i32, version_name: &str) -> Result<Vec<u8>, DomainError> {
    let manifest = domain::apk::encode_android_manifest(version_code, version_name);
    let mut cursor = Cursor::new(Vec::new());
    {
        let mut zip = ZipWriter::new(&mut cursor);
        zip.start_file("AndroidManifest.xml", SimpleFileOptions::default())
            .map_err(|e| DomainError::infra(e.to_string()))?;
        zip.write_all(&manifest)
            .map_err(|e| DomainError::infra(e.to_string()))?;
        zip.finish()
            .map_err(|e| DomainError::infra(e.to_string()))?;
    }
    Ok(cursor.into_inner())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_version_from_packaged_apk() {
        let apk = package_apk(7, "1.2.3").unwrap();
        let identity = inspect_apk(&apk).unwrap();
        assert_eq!(identity.version_code, 7);
        assert_eq!(identity.version_name, "1.2.3");
    }
}
