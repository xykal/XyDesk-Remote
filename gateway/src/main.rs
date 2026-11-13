//! XyDesk RDP Gateway — backend keamanan (Rust).
//!
//! Tugasnya:
//! 1. `POST /api/rdp/token` — menerbitkan token sesi terenkripsi (HMAC+AES)
//!    dari kredensial RDP yang dikirim browser. Tidak ada kredensial yang
//!    disimpan; token berumur ±120 detik.
//! 2. `GET /websocket-tunnel?token=...` — memverifikasi token, membuka koneksi
//!    TCP ke guacd, melakukan jabat tangan protokol RDP, lalu menjembatani
//!    WebSocket ↔ guacd dua arah (protokol guacd instruksi teks).
//! 3. `GET /healthz` — cek kesehatan.
//!
//! Env: `GATEWAY_SECRET` (wajib), `GUACD_ADDR` (default 127.0.0.1:4822),
//! `BIND` (default 0.0.0.0:8787), `ALLOW_ORIGIN` (default situs resmi).

mod guacd;
mod token;

use axum::extract::ws::{Message, WebSocket, WebSocketUpgrade};
use axum::extract::State;
use axum::http::{header, Method};
use axum::response::{IntoResponse, Response};
use axum::routing::{get, post};
use axum::{Json, Router};
use futures_util::{SinkExt, StreamExt};
use serde::Deserialize;
use serde_json::json;
use std::net::SocketAddr;
use std::sync::Arc;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpStream;

#[derive(Clone)]
struct Cfg {
    secret: Arc<String>,
    guacd_addr: Arc<String>,
    allow_origin: Arc<String>,
}

fn secret_from_env() -> Result<String, String> {
    std::env::var("GATEWAY_SECRET")
        .ok()
        .filter(|s| s.len() >= 16)
        .ok_or_else(|| "GATEWAY_SECRET wajib diisi (min 16 karakter)".to_string())
}

#[derive(Deserialize)]
struct ConnectReq {
    host: String,
    port: Option<u16>,
    user: Option<String>,
    pass: Option<String>,
    width: Option<u16>,
    height: Option<u16>,
}

fn cors_headers(origin: &str) -> [(header::HeaderName, &'static str); 3] {
    [
        (header::ACCESS_CONTROL_ALLOW_ORIGIN, Box::leak(origin.to_string().into_boxed_str())),
        (header::ACCESS_CONTROL_ALLOW_METHODS, "POST, GET, OPTIONS"),
        (header::ACCESS_CONTROL_ALLOW_HEADERS, "content-type"),
    ]
}

async fn issue_token(State(cfg): State<Cfg>, Json(req): Json<ConnectReq>) -> Response {
    if req.host.trim().is_empty() || req.host.len() > 255 {
        return Json(json!({"status": "error", "code": "host_invalid"})).into_response();
    }
    let t = token::RdpToken {
        host: req.host.trim().to_string(),
        port: req.port.unwrap_or(3389),
        user: req.user.unwrap_or_default(),
        pass: req.pass.unwrap_or_default(),
        width: req.width.unwrap_or(1600).clamp(640, 3840),
        height: req.height.unwrap_or(900).clamp(480, 2160),
        exp: 0,
    };
    let tok = token::issue(&cfg.secret, t);
    let mut resp = Json(json!({ "status": "ok", "token": tok })).into_response();
    for (k, v) in cors_headers(&cfg.allow_origin) {
        resp.headers_mut().insert(k, v.parse().unwrap());
    }
    resp
}

async fn healthz() -> &'static str {
    "ok"
}

async fn tunnel_ws(ws: WebSocketUpgrade, State(cfg): State<Cfg>) -> Response {
    ws.on_upgrade(move |socket| bridge(socket, cfg))
}

async fn bridge(ws: WebSocket, cfg: Cfg) {
    // Token harus datang sebagai query param ?token= — divalidasi sebelum pipa.
    // (Query dibaca oleh pemanggil upgrade; di sini kita terima lewat pesan
    // pertama bila query kosong — kompatibel dengan guacamole-common-js.)
    let (mut ws_tx, mut ws_rx) = ws.split();

    let first = match tokio::time::timeout(std::time::Duration::from_secs(10), ws_rx.next()).await {
        Ok(Some(Ok(Message::Text(t)))) => t,
        Ok(Some(Ok(Message::Binary(b)))) => String::from_utf8_lossy(&b).to_string(),
        _ => {
            let _ = ws_tx.send(Message::Text(r#"{"status":"error","code":"token_missing"}"#.into())).await;
            return;
        }
    };
    let tok = first.trim().to_string();
    let payload = match token::verify(&cfg.secret, &tok) {
        Ok(p) => p,
        Err(e) => {
            let _ = ws_tx
                .send(Message::Text(format!(r#"{{"status":"error","code":"token_rejected","message":"{e}"}}"#)))
                .await;
            return;
        }
    };

    let mut guacd = match TcpStream::connect(cfg.guacd_addr.as_str()).await {
        Ok(s) => s,
        Err(e) => {
            let _ = ws_tx
                .send(Message::Text(format!(r#"{{"status":"error","code":"guacd_unreachable","message":"{e}"}}"#)))
                .await;
            return;
        }
    };

    let hs = guacd::handshake_rdp(
        &payload.host,
        payload.port,
        &payload.user,
        &payload.pass,
        payload.width,
        payload.height,
    );
    if guacd.write_all(hs.as_bytes()).await.is_err() {
        let _ = ws_tx.send(Message::Text(r#"{"status":"error","code":"guacd_write"}"#.into())).await;
        return;
    }

    // Pipa dua arah: WS text/binary <-> TCP guacd.
    let (mut gr, mut gw) = guacd.split();
    let mut buf = vec![0u8; 16 * 1024];
    loop {
        tokio::select! {
            chunk = gr.read(&mut buf) => {
                match chunk {
                    Ok(0) | Err(_) => break,
                    Ok(n) => {
                        if ws_tx.send(Message::Text(String::from_utf8_lossy(&buf[..n]).to_string())).await.is_err() {
                            break;
                        }
                    }
                }
            }
            msg = ws_rx.next() => {
                match msg {
                    Some(Ok(Message::Text(t))) => {
                        if gw.write_all(t.as_bytes()).await.is_err() { break; }
                    }
                    Some(Ok(Message::Binary(b))) => {
                        if gw.write_all(&b).await.is_err() { break; }
                    }
                    Some(Ok(_)) => {}
                    _ => break,
                }
            }
        }
    }
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let secret = Arc::new(secret_from_env().map_err(|e| e)?);
    let guacd_addr = Arc::new(std::env::var("GUACD_ADDR").unwrap_or_else(|_| "127.0.0.1:4822".into()));
    let allow_origin = Arc::new(
        std::env::var("ALLOW_ORIGIN").unwrap_or_else(|_| "https://www.xydeskremote.biz.id".into()),
    );
    let cfg = Cfg { secret, guacd_addr, allow_origin };

    let app = Router::new()
        .route("/healthz", get(healthz))
        .route("/api/rdp/token", post(issue_token))
        .route("/websocket-tunnel", get(tunnel_ws))
        .with_state(cfg);

    let bind = std::env::var("BIND").unwrap_or_else(|_| "0.0.0.0:8787".into());
    let addr: SocketAddr = bind.parse()?;
    let listener = tokio::net::TcpListener::bind(addr).await?;
    eprintln!("xydesk-rdp-gateway listening on {addr}");
    axum::serve(listener, app).await?;
    Ok(())
}
