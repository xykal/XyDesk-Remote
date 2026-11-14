import './style.css';
export const metadata = {
 metadataBase:new URL('https://www.xydeskremote.biz.id'),
 title:'XyDesk Remote — Remote desktop dari Android',
 description:'Hubungkan Android ke PC Windows atau cloud PC dengan XyDesk Remote.',
 alternates:{canonical:'/'},
 openGraph:{title:'XyDesk Remote — Remote desktop dari Android',description:'Akses dan kendalikan desktop dari Android.',url:'https://www.xydeskremote.biz.id/',siteName:'XyDesk Remote',type:'website',images:['/og-xydesk-remote.png']},
 twitter:{card:'summary_large_image',title:'XyDesk Remote',description:'Akses desktop Windows dari Android.',images:['/og-xydesk-remote.png']},
 icons:{icon:'/favicon.svg',apple:'/apple-touch-icon.png'},manifest:'/manifest.json'
};
export default function RootLayout({children}) {return <html lang="id"><body>{children}</body></html>}
