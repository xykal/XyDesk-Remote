/*!
 * XyDesk Remote — mesin multi-bahasa web.
 * 20 bahasa bawaan + auto-translate 100+ bahasa lewat /api/ui/translate
 * (hasilnya di-cache di localStorage supaya hemat kuota & instan).
 * Sumber teks = kamus EN (window.__xyEnDict) + teks asli halaman (ID).
 */
(function () {
  var LANGS = {
    id: "Indonesia", en: "English", ms: "Bahasa Melayu", es: "Español",
    pt: "Português", fr: "Français", de: "Deutsch", ru: "Русский",
    ar: "العربية", hi: "हिन्दी", ja: "日本語", ko: "한국어", zh: "中文",
    vi: "Tiếng Việt", th: "ภาษาไทย", tr: "Türkçe", it: "Italiano",
    nl: "Nederlands", pl: "Polski", uk: "Українська"
  };
  var CACHE_KEY = "xy_lang_cache_v2";
  var orig = null;

  function cacheRead() {
    try { return JSON.parse(localStorage.getItem(CACHE_KEY) || "{}"); } catch (e) { return {}; }
  }
  function cacheWrite(c) {
    try { localStorage.setItem(CACHE_KEY, JSON.stringify(c)); } catch (e) {}
  }
  function apiCode(l) { return (l === "zh" ? "zh-CN" : l).toUpperCase(); }

  function capture() {
    if (orig) return;
    orig = {};
    document.querySelectorAll("[data-i18n]").forEach(function (el) {
      var k = el.getAttribute("data-i18n");
      if (!(k in orig)) orig[k] = el.textContent;
    });
  }

  function paint(lang, bag, en) {
    document.querySelectorAll("[data-i18n]").forEach(function (el) {
      var k = el.getAttribute("data-i18n");
      var v = bag[k] || (lang === "id" ? null : en[k]) || orig[k];
      if (v) el.textContent = v;
    });
  }

  function apply(l) {
    capture();
    var en = window.__xyEnDict || {};
    var lang = (l || "id").toLowerCase();
    if (!LANGS[lang]) lang = "en";
    document.documentElement.setAttribute("lang", lang === "zh" ? "zh-CN" : lang);
    try { localStorage.setItem("xydesk_lang", lang); } catch (e) {}
    if (lang === "id") { paint("id", {}, en); return Promise.resolve(); }
    if (lang === "en") { paint("en", en, en); return Promise.resolve(); }

    var c = cacheRead();
    var bag = c[lang] || {};
    paint(lang, bag, en);

    var missing = [];
    Object.keys(orig).forEach(function (k) { if (!bag[k]) missing.push(k); });
    if (!missing.length) return Promise.resolve();

    // Endpoint batch menerima maksimal 40 item per permintaan.
    var chunks = [];
    for (var i = 0; i < missing.length; i += 40) chunks.push(missing.slice(i, i + 40));
    var chain = Promise.resolve();
    chunks.forEach(function (keys) {
      chain = chain.then(function () {
        var items = keys.map(function (k) { return en[k] || orig[k] || ""; });
        return fetch("/api/ui/translate", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ target: apiCode(lang), items: items })
        }).then(function (r) { return r.json(); }).then(function (j) {
          if (!j || j.status !== "ok" || !j.items) return;
          var c2 = cacheRead();
          var bag2 = c2[lang] || {};
          keys.forEach(function (k, i2) { if (j.items[i2]) bag2[k] = j.items[i2]; });
          c2[lang] = bag2; cacheWrite(c2);
          paint(lang, bag2, en);
        }).catch(function () {});
      });
    });
    return chain;
  }

  function buildSelect(select) {
    if (!select) return;
    var stored = "id";
    try { stored = localStorage.getItem("xydesk_lang") || "id"; } catch (e) {}
    select.innerHTML = "";
    Object.keys(LANGS).forEach(function (code) {
      var opt = document.createElement("option");
      opt.value = code;
      opt.textContent = LANGS[code];
      if (code === stored) opt.selected = true;
      select.appendChild(opt);
    });
    select.addEventListener("change", function () {
      apply(select.value);
      try { var t = new URL(location.href); t.searchParams.delete("lang"); history.replaceState(null, "", t.pathname + t.hash); } catch (e) {}
    });
  }

  window.XyWebLang = { apply: apply, langs: LANGS, buildSelect: buildSelect };

  document.addEventListener("DOMContentLoaded", function () {
    buildSelect(document.getElementById("xyLangSelect"));
    var q = null;
    try { q = new URL(location.href).searchParams.get("lang"); } catch (e) {}
    var want = q && LANGS[q.toLowerCase()] ? q.toLowerCase() : null;
    if (want) { apply(want); return; }
    var stored = "id";
    try { stored = localStorage.getItem("xydesk_lang") || "id"; } catch (e) {}
    if (stored && stored !== "id") apply(stored);
  });
})();
