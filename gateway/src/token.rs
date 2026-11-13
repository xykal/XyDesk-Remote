//! Token sesi RDP — AES-256-CBC + HMAC-SHA256 (urut: HMAC dulu, baru dekripsi).
//! Format token: base64url(iv || ciphertext || hmac_tag).
//! Kata sandi pengguna TIDAK PERNAH disimpan di server — hanya lewat token
//! berumur pendek menuju guacd.

use aes::cipher::{block_padding::Pkcs7, BlockDecryptMut, BlockEncryptMut, KeyIvInit};
use base64::Engine;
use hmac::{Hmac, Mac};
use rand::RngCore;
use serde::{Deserialize, Serialize};
use sha2::Sha256;

type Aes256CbcEnc = cbc::Encryptor<aes::Aes256>;
type Aes256CbcDec = cbc::Decryptor<aes::Aes256>;

pub const TOKEN_TTL_SECS: u64 = 120;

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
pub struct RdpToken {
    pub host: String,
    pub port: u16,
    pub user: String,
    pub pass: String,
    pub width: u16,
    pub height: u16,
    /// Unix detik kedaluwarsa.
    pub exp: u64,
}

fn derive(secret: &str) -> ([u8; 32], [u8; 32]) {
    let mut mac = Hmac::<Sha256>::new_from_slice(secret.as_bytes()).expect("hmac key");
    mac.update(b"xydesk-rdp-gateway/v1");
    let out = mac.finalize().into_bytes();
    let mut enc_key = [0u8; 32];
    let mut mac_key = [0u8; 32];
    enc_key.copy_from_slice(&out[..32]);
    // Kunci MAC terpisah: HMAC(kunci_enkripsi || domain) — turunan sederhana.
    let mut mac2 = Hmac::<Sha256>::new_from_slice(&out).expect("hmac key2");
    mac2.update(b"tag");
    mac_key.copy_from_slice(&mac2.finalize().into_bytes()[..32]);
    (enc_key, mac_key)
}

pub fn now_secs() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

/// Terbitkan token terenkripsi untuk satu sesi RDP.
pub fn issue(secret: &str, mut t: RdpToken) -> String {
    if t.exp == 0 {
        t.exp = now_secs() + TOKEN_TTL_SECS;
    }
    let (enc_key, mac_key) = derive(secret);
    let json = serde_json::to_vec(&t).expect("serialize token");
    let mut iv = [0u8; 16];
    rand::thread_rng().fill_bytes(&mut iv);
    let ct = Aes256CbcEnc::new(&enc_key.into(), &iv.into())
        .encrypt_padded_vec_mut::<Pkcs7>(&json);
    let mut blob = Vec::with_capacity(16 + ct.len() + 32);
    blob.extend_from_slice(&iv);
    blob.extend_from_slice(&ct);
    let mut tag = Hmac::<Sha256>::new_from_slice(&mac_key).expect("hmac");
    tag.update(&blob);
    blob.extend_from_slice(&tag.finalize().into_bytes());
    base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(blob)
}

/// Verifikasi integritas + kedaluwarsa, lalu dekripsi payload.
pub fn verify(secret: &str, token: &str) -> Result<RdpToken, String> {
    let blob = base64::engine::general_purpose::URL_SAFE_NO_PAD
        .decode(token)
        .map_err(|_| "token tidak valid (base64)".to_string())?;
    if blob.len() < 16 + 32 + 1 {
        return Err("token terlalu pendek".into());
    }
    let (body, tag) = blob.split_at(blob.len() - 32);
    let (enc_key, mac_key) = derive(secret);
    let mut mac = Hmac::<Sha256>::new_from_slice(&mac_key).expect("hmac");
    mac.update(body);
    mac.verify_slice(tag)
        .map_err(|_| "token ditandatangani pihak lain / dimodifikasi".to_string())?;
    let (iv, ct) = body.split_at(16);
    let json = Aes256CbcDec::new(&enc_key.into(), iv.into())
        .decrypt_padded_vec_mut::<Pkcs7>(ct)
        .map_err(|_| "token tidak bisa didekripsi".to_string())?;
    let t: RdpToken =
        serde_json::from_slice(&json).map_err(|_| "payload token rusak".to_string())?;
    if t.exp < now_secs() {
        return Err("token kedaluwarsa".into());
    }
    Ok(t)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample() -> RdpToken {
        RdpToken {
            host: "192.168.1.10".into(),
            port: 3389,
            user: "Administrator".into(),
            pass: "rahasia123".into(),
            width: 1600,
            height: 900,
            exp: now_secs() + 60,
        }
    }

    #[test]
    fn roundtrip() {
        let tok = issue("s3cret", sample());
        let got = verify("s3cret", &tok).expect("verify");
        assert_eq!(got, sample());
    }

    #[test]
    fn tamper_ditolak() {
        let tok = issue("s3cret", sample());
        let mut chars: Vec<u8> = tok.into_bytes();
        // Ubah 1 karakter payload -> HMAC gagal.
        let i = chars.len() / 2;
        chars[i] = if chars[i] == b'A' { b'B' } else { b'A' };
        let evil = String::from_utf8(chars).unwrap();
        assert!(verify("s3cret", &evil).is_err());
    }

    #[test]
    fn secret_beda_ditolak() {
        let tok = issue("s3cret", sample());
        assert!(verify("lain", &tok).is_err());
    }

    #[test]
    fn kedaluwarsa_ditolak() {
        let mut t = sample();
        t.exp = now_secs() - 1;
        let tok = issue("s3cret", t);
        assert!(verify("s3cret", &tok).is_err());
    }

    #[test]
    fn token_sampah_ditolak() {
        assert!(verify("s3cret", "bukan-token").is_err());
        assert!(verify("s3cret", "").is_err());
    }
}
