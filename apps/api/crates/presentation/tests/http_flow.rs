use std::net::SocketAddr;
use std::sync::Arc;

use application::Services;
use infrastructure::config::Settings;
use infrastructure::build_deps;
use reqwest::multipart;
use serde_json::Value;
use tokio::net::TcpListener;

async fn spawn_app() -> (String, reqwest::Client) {
    let settings = Settings {
        database_url: String::new(),
        jwt_secret: "test-secret".into(),
        api_host: "127.0.0.1".into(),
        api_port: 0,
        api_public_url: "http://127.0.0.1".into(),
        minio_endpoint: String::new(),
        minio_bucket: "mydrive".into(),
        minio_region: "us-east-1".into(),
        minio_access_key: String::new(),
        minio_secret_key: String::new(),
        mdns_enable: false,
        mdns_service_name: "my-drive".into(),
        memory_backend: true,
        musicdl_url: "http://127.0.0.1:8090".into(),
    };
    let deps = build_deps(&settings).await.unwrap();
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = listener.local_addr().unwrap();
    let public = format!("http://{addr}");
    let app = mydrive_api::router(mydrive_api::state_from_services(
        public.clone(),
        Arc::new(Services::new(deps)),
    ));
    tokio::spawn(async move {
        axum::serve(listener, app).await.unwrap();
    });
    (public, reqwest::Client::new())
}

#[tokio::test]
async fn setup_upload_and_manifest_flow() {
    let (base, client) = spawn_app().await;

    let health: Value = client
        .get(format!("{base}/health"))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(health["status"], "ok");

    let setup: Value = client
        .post(format!("{base}/v1/setup"))
        .json(&serde_json::json!({"username":"admin","password":"password123"}))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    let token = setup["token"].as_str().unwrap();

    let form = multipart::Form::new().part(
        "file",
        multipart::Part::bytes(b"\xFF\xD8\xFF fakejpeg")
            .file_name("photo.jpg")
            .mime_str("image/jpeg")
            .unwrap(),
    );
    let uploaded: Value = client
        .post(format!("{base}/v1/files"))
        .bearer_auth(token)
        .multipart(form)
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(uploaded["name"], "photo.jpg");
    let content_path = uploaded["content_url"].as_str().unwrap();
    let content = client
        .get(format!("{base}{content_path}"))
        .bearer_auth(token)
        .send()
        .await
        .unwrap();
    assert!(content.status().is_success());

    let device: Value = client
        .post(format!("{base}/v1/devices"))
        .bearer_auth(token)
        .json(&serde_json::json!({"name":"Phone A"}))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();

    let manifest: Value = client
        .post(format!("{base}/v1/sync/manifest"))
        .bearer_auth(token)
        .json(&serde_json::json!({
            "device_id": device["id"],
            "have_file_ids": []
        }))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(manifest["files"].as_array().unwrap().len(), 1);
    assert!(manifest["files"][0]["url"].as_str().unwrap().contains("/content"));
    assert_eq!(manifest["files"][0]["media_kind"], "photo");
    assert!(manifest["albums"].as_array().unwrap().is_empty());

    let file_id = uploaded["id"].as_str().unwrap();
    let trashed: Value = client
        .post(format!("{base}/v1/files/{file_id}/trash"))
        .bearer_auth(token)
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert!(trashed["deleted_at"].as_str().is_some());
    let library: Value = client
        .get(format!("{base}/v1/files?silo=photos"))
        .bearer_auth(token)
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert!(library.as_array().unwrap().is_empty());
    let bin: Value = client
        .get(format!("{base}/v1/files/trash?silo=photos"))
        .bearer_auth(token)
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(bin.as_array().unwrap().len(), 1);
    let after_trash: Value = client
        .post(format!("{base}/v1/sync/manifest"))
        .bearer_auth(token)
        .json(&serde_json::json!({
            "device_id": device["id"],
            "have_file_ids": [file_id]
        }))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert!(after_trash["files"].as_array().unwrap().is_empty());
    assert_eq!(
        after_trash["removed"].as_array().unwrap(),
        &vec![serde_json::json!(file_id)]
    );
    let restored: Value = client
        .post(format!("{base}/v1/files/{file_id}/restore"))
        .bearer_auth(token)
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert!(restored["deleted_at"].is_null());
    let after_restore: Value = client
        .post(format!("{base}/v1/sync/manifest"))
        .bearer_auth(token)
        .json(&serde_json::json!({
            "device_id": device["id"],
            "have_file_ids": [file_id]
        }))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(after_restore["files"].as_array().unwrap().len(), 1);
    assert!(after_restore["removed"].as_array().unwrap().is_empty());

    let latest = client
        .get(format!("{base}/v1/app/releases/latest"))
        .bearer_auth(token)
        .send()
        .await
        .unwrap();
    assert_eq!(latest.status(), 404);

    let apk_bytes = infrastructure::apk::package_apk(2, "0.2.0").unwrap();
    let inspect: Value = client
        .post(format!("{base}/v1/app/releases/inspect"))
        .bearer_auth(token)
        .multipart(
            multipart::Form::new().part(
                "apk",
                multipart::Part::bytes(apk_bytes.clone())
                    .file_name("my-drive-0.2.0.apk")
                    .mime_str("application/vnd.android.package-archive")
                    .unwrap(),
            ),
        )
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(inspect["version_code"], 2);
    assert_eq!(inspect["version_name"], "0.2.0");

    let apk_form = multipart::Form::new()
        .text("changelog", "LAN sync and library browser")
        .part(
            "apk",
            multipart::Part::bytes(apk_bytes.clone())
                .file_name("my-drive-0.2.0.apk")
                .mime_str("application/vnd.android.package-archive")
                .unwrap(),
        );
    let published: Value = client
        .post(format!("{base}/v1/app/releases"))
        .bearer_auth(token)
        .multipart(apk_form)
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(published["version_code"], 2);
    assert_eq!(published["version_name"], "0.2.0");

    let release_id = published["id"].as_str().unwrap();
    let updated: Value = client
        .patch(format!("{base}/v1/app/releases/{release_id}"))
        .bearer_auth(token)
        .multipart(multipart::Form::new().text("changelog", "notes for 0.2.0"))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(updated["changelog"], "notes for 0.2.0");
    assert_eq!(updated["version_code"], 2);

    let latest: Value = client
        .get(format!("{base}/v1/app/releases/latest"))
        .bearer_auth(token)
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(latest["version_code"], 2);
    assert!(latest["download_url"].as_str().unwrap().contains("/apk"));

    let apk_path = latest["download_url"].as_str().unwrap();
    let apk_url = if apk_path.starts_with("http") {
        apk_path.to_string()
    } else {
        format!("{base}{apk_path}")
    };
    let downloaded = client
        .get(apk_url)
        .bearer_auth(token)
        .send()
        .await
        .unwrap()
        .bytes()
        .await
        .unwrap();
    assert_eq!(&downloaded[..], apk_bytes);

    let deleted = client
        .delete(format!("{base}/v1/app/releases/{release_id}"))
        .bearer_auth(token)
        .send()
        .await
        .unwrap();
    assert_eq!(deleted.status(), 204);
    let missing = client
        .get(format!("{base}/v1/app/releases/latest"))
        .bearer_auth(token)
        .send()
        .await
        .unwrap();
    assert_eq!(missing.status(), 404);
}

#[tokio::test]
async fn typed_albums_rename_and_file_updates() {
    let (base, client) = spawn_app().await;
    let setup: Value = client
        .post(format!("{base}/v1/setup"))
        .json(&serde_json::json!({"username":"admin","password":"password123"}))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    let token = setup["token"].as_str().unwrap();

    let photos: Value = client
        .post(format!("{base}/v1/albums"))
        .bearer_auth(token)
        .json(&serde_json::json!({"name":"Trip","silo":"photos"}))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(photos["silo"], "photos");
    let album_id = photos["id"].as_str().unwrap();

    let music: Value = client
        .post(format!("{base}/v1/albums"))
        .bearer_auth(token)
        .json(&serde_json::json!({"name":"Jazz","silo":"music"}))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(music["silo"], "music");

    let listed: Value = client
        .get(format!("{base}/v1/albums?silo=photos"))
        .bearer_auth(token)
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(listed.as_array().unwrap().len(), 1);
    assert_eq!(listed[0]["name"], "Trip");

    let form = multipart::Form::new().part(
        "file",
        multipart::Part::bytes(b"\xFF\xD8\xFF fakejpeg")
            .file_name("photo.jpg")
            .mime_str("image/jpeg")
            .unwrap(),
    );
    let uploaded: Value = client
        .post(format!("{base}/v1/files"))
        .bearer_auth(token)
        .multipart(form)
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    let file_id = uploaded["id"].as_str().unwrap();

    let renamed: Value = client
        .patch(format!("{base}/v1/files/{file_id}"))
        .bearer_auth(token)
        .json(&serde_json::json!({"name":"holiday.jpg","album_id": album_id}))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(renamed["name"], "holiday.jpg");
    assert_eq!(renamed["album_id"], album_id);

    let wrong = client
        .patch(format!("{base}/v1/files/{file_id}"))
        .bearer_auth(token)
        .json(&serde_json::json!({"album_id": music["id"]}))
        .send()
        .await
        .unwrap();
    assert_eq!(wrong.status(), 400);

    let album_renamed: Value = client
        .patch(format!("{base}/v1/albums/{album_id}"))
        .bearer_auth(token)
        .json(&serde_json::json!({"name":"Summer"}))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(album_renamed["name"], "Summer");

    let device: Value = client
        .post(format!("{base}/v1/devices"))
        .bearer_auth(token)
        .json(&serde_json::json!({"name":"Phone A"}))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    let manifest: Value = client
        .post(format!("{base}/v1/sync/manifest"))
        .bearer_auth(token)
        .json(&serde_json::json!({
            "device_id": device["id"],
            "have_file_ids": [file_id]
        }))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(manifest["files"].as_array().unwrap().len(), 1);
    assert_eq!(manifest["files"][0]["name"], "holiday.jpg");
    assert_eq!(manifest["files"][0]["album_id"], album_id);
    assert_eq!(manifest["albums"].as_array().unwrap().len(), 2);

    let deleted = client
        .delete(format!("{base}/v1/albums/{album_id}"))
        .bearer_auth(token)
        .send()
        .await
        .unwrap();
    assert_eq!(deleted.status(), 204);
    let file: Value = client
        .get(format!("{base}/v1/files/{file_id}"))
        .bearer_auth(token)
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert!(file["album_id"].is_null());
}

#[tokio::test]
async fn register_users_share_exclusions_and_mobile_audio() {
    let (base, client) = spawn_app().await;
    let setup: Value = client
        .post(format!("{base}/v1/setup"))
        .json(&serde_json::json!({"username":"admin","password":"password123"}))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    let admin_token = setup["token"].as_str().unwrap();

    let too_early = client
        .post(format!("{base}/v1/setup"))
        .json(&serde_json::json!({"username":"other","password":"password123"}))
        .send()
        .await
        .unwrap();
    assert_eq!(too_early.status(), 409);

    let registered: Value = client
        .post(format!("{base}/v1/register"))
        .json(&serde_json::json!({"username":"friend","password":"password123"}))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    let friend_token = registered["token"].as_str().unwrap();
    let friend_id = registered["user"]["id"].as_str().unwrap();

    let duplicate = client
        .post(format!("{base}/v1/register"))
        .json(&serde_json::json!({"username":"friend","password":"password123"}))
        .send()
        .await
        .unwrap();
    assert_eq!(duplicate.status(), 409);

    let users: Value = client
        .get(format!("{base}/v1/users"))
        .bearer_auth(admin_token)
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(users.as_array().unwrap().len(), 2);
    assert!(users[0]["password_hash"].is_null() || users[0].get("password_hash").is_none());

    let form = multipart::Form::new().part(
        "file",
        multipart::Part::bytes(b"\xFF\xD8\xFF fakejpeg")
            .file_name("photo.jpg")
            .mime_str("image/jpeg")
            .unwrap(),
    );
    let uploaded: Value = client
        .post(format!("{base}/v1/files"))
        .bearer_auth(admin_token)
        .multipart(form)
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    let file_id = uploaded["id"].as_str().unwrap();

    let denied = client
        .get(format!("{base}/v1/files/{file_id}"))
        .bearer_auth(friend_token)
        .send()
        .await
        .unwrap();
    assert_eq!(denied.status(), 403);

    let share: Value = client
        .post(format!("{base}/v1/shares"))
        .bearer_auth(admin_token)
        .json(&serde_json::json!({
            "resource_type": "file",
            "resource_id": file_id,
            "grantee_id": friend_id,
            "permission": "read"
        }))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(share["grantee_username"], "friend");

    let allowed: Value = client
        .get(format!("{base}/v1/files/{file_id}"))
        .bearer_auth(friend_token)
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(allowed["access"], "read");
    assert_eq!(allowed["shared"], true);

    let trash = client
        .post(format!("{base}/v1/files/{file_id}/trash"))
        .bearer_auth(friend_token)
        .send()
        .await
        .unwrap();
    assert_eq!(trash.status(), 403);

    let device: Value = client
        .post(format!("{base}/v1/devices"))
        .bearer_auth(admin_token)
        .json(&serde_json::json!({"name":"Phone A"}))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    let device_id = device["id"].as_str().unwrap();
    let exclusions: Value = client
        .put(format!("{base}/v1/devices/{device_id}/exclusions"))
        .bearer_auth(admin_token)
        .json(&serde_json::json!({"file_ids": [file_id]}))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert_eq!(exclusions["file_ids"].as_array().unwrap().len(), 1);

    let manifest: Value = client
        .post(format!("{base}/v1/sync/manifest"))
        .bearer_auth(admin_token)
        .json(&serde_json::json!({
            "device_id": device_id,
            "have_file_ids": []
        }))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    assert!(manifest["files"].as_array().unwrap().is_empty());

    let flac = multipart::Form::new().part(
        "file",
        multipart::Part::bytes(b"fLaCnotreally")
            .file_name("song.flac")
            .mime_str("audio/flac")
            .unwrap(),
    );
    let flac_file: Value = client
        .post(format!("{base}/v1/files"))
        .bearer_auth(admin_token)
        .multipart(flac)
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    let flac_id = flac_file["id"].as_str().unwrap();
    let original = client
        .get(format!("{base}/v1/files/{flac_id}/content"))
        .bearer_auth(admin_token)
        .send()
        .await
        .unwrap()
        .bytes()
        .await
        .unwrap();
    assert_eq!(&original[..], b"fLaCnotreally");
    let mobile = client
        .get(format!("{base}/v1/files/{flac_id}/content?variant=mobile"))
        .bearer_auth(admin_token)
        .send()
        .await
        .unwrap();
    assert_eq!(
        mobile.headers().get("content-type").unwrap(),
        "audio/mp4"
    );
    let mobile_bytes = mobile.bytes().await.unwrap();
    assert_eq!(&mobile_bytes[..], b"fake-aac");

    let friend_device: Value = client
        .post(format!("{base}/v1/devices"))
        .bearer_auth(admin_token)
        .json(&serde_json::json!({"name":"Phone B"}))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    let music_manifest: Value = client
        .post(format!("{base}/v1/sync/manifest"))
        .bearer_auth(admin_token)
        .json(&serde_json::json!({
            "device_id": friend_device["id"],
            "have_file_ids": []
        }))
        .send()
        .await
        .unwrap()
        .json()
        .await
        .unwrap();
    let audio = music_manifest["files"]
        .as_array()
        .unwrap()
        .iter()
        .find(|f| f["id"] == flac_id)
        .unwrap();
    assert_eq!(audio["mime"], "audio/mp4");
    assert!(audio["url"].as_str().unwrap().contains("variant=mobile"));
}

#[allow(dead_code)]
fn _keep_addr_type(_: SocketAddr) {}
