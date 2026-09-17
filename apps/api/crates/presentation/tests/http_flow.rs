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

    let latest = client
        .get(format!("{base}/v1/app/releases/latest"))
        .bearer_auth(token)
        .send()
        .await
        .unwrap();
    assert_eq!(latest.status(), 404);

    let apk_form = multipart::Form::new()
        .text("version_code", "2")
        .text("version_name", "0.2.0")
        .text("changelog", "LAN sync and library browser")
        .part(
            "apk",
            multipart::Part::bytes(b"fake-apk-bytes")
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

    let apk_bytes = client
        .get(latest["download_url"].as_str().unwrap())
        .bearer_auth(token)
        .send()
        .await
        .unwrap()
        .bytes()
        .await
        .unwrap();
    assert_eq!(&apk_bytes[..], b"fake-apk-bytes");
}

#[allow(dead_code)]
fn _keep_addr_type(_: SocketAddr) {}
