(function () {
  const icons = {
    home:'<path d="m3 11 9-7 9 7"/><path d="M5 10v10h14V10M9 20v-6h6v6"/>',
    grid:'<rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/>',
    screen:'<rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8m-4-4v4"/>',
    download:'<path d="M12 3v12m-4-4 4 4 4-4M4 20h16"/>'
  };
  const links = [['home','Beranda','/#beranda'],['grid','Fitur','/#fitur'],['screen','RDP Web','/rdp.html'],['download','Unduh','/#unduh']];
  const nav = document.createElement('nav');
  nav.className='next-bottom'; nav.setAttribute('aria-label','Navigasi cepat');
  for (const [icon,label,href] of links) {
    const a=document.createElement('a');a.href=href;
    a.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'+icons[icon]+'</svg><span>'+label+'</span>';
    if (icon==='screen' && /\/rdp(?:\.html)?\/?$/.test(location.pathname)) a.setAttribute('aria-current','page');
    nav.appendChild(a);
  }
  document.body.appendChild(nav);
  for (const table of document.querySelectorAll('table.abi-table')) {
    const wrap = document.createElement('div');
    wrap.className = 'next-table-scroll';
    table.parentNode.insertBefore(wrap, table);
    wrap.appendChild(table);
  }
  const stage = document.getElementById('stage');
  if (stage) {
    const sync = () => { nav.hidden = getComputedStyle(stage).display !== 'none'; };
    new MutationObserver(sync).observe(stage, {attributes:true, attributeFilter:['style','class']});
    sync();
  }
})();
