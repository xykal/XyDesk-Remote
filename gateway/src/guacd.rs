//! Framing protokol guacd (instruction set berformat `len.teks,` dipisah `,`
//! dan diakhiri `;`). Dipakai untuk jabat tangan: `select` → `args` → `size`
//! → `audio` → `video` → `connect`, lalu sisa sesi = pipa instruksi mentah.

/// Satu instruksi guacd: `4.size,4.1600,3.900;` dst.
pub fn encode(name: &str, args: &[&str]) -> String {
    let mut out = String::new();
    for (i, part) in std::iter::once(name).chain(args.iter().copied()).enumerate() {
        if i > 0 {
            out.push(',');
        }
        out.push_str(&format!("{}.", part.len()));
        out.push_str(part);
    }
    out.push(';');
    out
}

/// Sekuens jabat tangan awal menuju guacd untuk protokol `rdp`.
pub fn handshake_rdp(
    host: &str,
    port: u16,
    user: &str,
    pass: &str,
    width: u16,
    height: u16,
) -> String {
    let port_s = port.to_string();
    let w = width.to_string();
    let h = height.to_string();
    let mut out = String::new();
    out.push_str(&encode("select", &["rdp"]));
    out.push_str(&encode(
        "args",
        &[
            host,
            port_s.as_str(),
            user,
            pass,
            "", // domain
            "", // program
            "", // directory
            "0", // console
            "", // security (negosiasi)
            "0", // ignore-cert (0 = verifikasi; gateway TLS sudah menjamin jalur)
            "", // initial-program
        ],
    ));
    out.push_str(&encode("size", &[w.as_str(), h.as_str()]));
    out.push_str(&encode("audio", &["audio/L16"]));
    out.push_str(&encode("video", &[]));
    out.push_str(&encode("connect", &[]));
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn format_instruksi_sesuai_protokol() {
        assert_eq!(encode("size", &["1600", "900"]), "4.size,4.1600,3.900;");
        assert_eq!(encode("connect", &[]), "7.connect;");
    }

    #[test]
    fn handshake_diawali_select_rdp() {
        let hs = handshake_rdp("192.168.1.10", 3389, "admin", "pw", 1280, 720);
        assert!(hs.starts_with_with_select(), "{}", hs);
        assert!(hs.contains("4.args,"));
        assert!(hs.contains("12.192.168.1.10"));
        assert!(hs.ends_with("7.connect;"));
        // setiap segmen berakhiran ';'
        assert!(hs.split(';').filter(|s| !s.is_empty()).count() >= 6);
    }

    trait StartsSelect {
        fn starts_with_with_select(&self) -> bool;
    }
    impl StartsSelect for String {
        fn starts_with_with_select(&self) -> bool {
            self.starts_with("6.select,3.rdp;")
        }
    }

    #[test]
    fn arg_tidak_mengandung_pemisah_mentah() {
        // panjang dihitung benar walau ada titik/koma di nilai
        let s = encode("args", &["a.b,c"]);
        assert_eq!(s, "4.args,5.a.b,c;");
    }
}
