const fs=require('fs');
const path=require('path');
const {chromium}=require('playwright');
(async()=>{
 const evidencia=path.join(__dirname,'evidencias');
 const ids=JSON.parse(fs.readFileSync(path.join(evidencia,'api-android.json'),'utf8'));
 const browser=await chromium.launch({channel:'msedge',headless:true});
 const context=await browser.newContext({viewport:{width:1280,height:820}});
 const page=await context.newPage();
 const resultados=[];
 try {
  for(const [campo,nombre,estado] of [['creado','ROOM PUBLICADO QA','ACTIVO'],['editado','ROOM EDITADO QA','ACTIVO'],['recuperado','ROOM RECUPERADO QA','RECUPERADO']]) {
   const api=await context.request.get(`http://127.0.0.1:18083/api/objetos/${ids[campo]}`);
   if(api.status()!==200)throw new Error(`API ${campo}: ${api.status()}`);
   const dato=await api.json();if(dato.nombre!==nombre || dato.estado!==estado)throw new Error(`Datos distintos en ${campo}`);
   await page.goto(`http://127.0.0.1:15176/objeto/${ids[campo]}`);
   await page.getByText(nombre,{exact:true}).first().waitFor();
   if(await page.evaluate(()=>localStorage.getItem('token'))!==null)throw new Error('La comprobación pública tiene sesión');
   await page.screenshot({path:path.join(evidencia,`web-${campo}.png`),fullPage:true});
   resultados.push({caso:campo,resultado:'correcto',estado,apiReal:true});console.log('OK web:',campo);
  }
  const retirado=await context.request.get(`http://127.0.0.1:18083/api/objetos/${ids.eliminado}`);
  if(retirado.status()!==404)throw new Error('Eliminado todavía visible');
  await page.goto(`http://127.0.0.1:15176/objeto/${ids.eliminado}`);
  await page.getByText('Objeto no encontrado',{exact:true}).waitFor();
  await page.screenshot({path:path.join(evidencia,'web-eliminado.png'),fullPage:true});
  resultados.push({caso:'eliminado',resultado:'correcto',apiReal:true});console.log('OK web: eliminado');
  fs.writeFileSync(path.join(evidencia,'web.json'),JSON.stringify({entorno:'Web existente + API QA + MySQL QA, sin sesión web',comprobaciones:resultados},null,2));
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
