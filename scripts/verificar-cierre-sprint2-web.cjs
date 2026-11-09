const fs=require('fs');const path=require('path');const {chromium}=require('playwright');
const raiz=path.resolve(__dirname,'..');const privado=path.join(raiz,'tmp/cierre-sprint2-20261103');const salida=path.join(raiz,'docs/pruebas/cierre-sprint2-20261103');
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try {
  const context=await browser.newContext({viewport:{width:390,height:844}});const page=await context.newPage();
  if(process.argv[2]==='enlace') {
   const correo=JSON.parse(fs.readFileSync(path.join(privado,'enlace-correo.json'),'utf8'));
   await page.goto('http://127.0.0.1:15176/restablecer-password?token='+encodeURIComponent(correo.token));
   const enlace=page.getByRole('link',{name:'Abrir en FoundIA Android'});await enlace.waitFor();
   const uri=await enlace.getAttribute('href');if(uri!=='foundia://restablecer?token='+encodeURIComponent(correo.token))throw new Error('Enlace distinto');
   await page.waitForURL('**/restablecer-password');if(new URL(page.url()).search)throw new Error('Token visible');
   await page.screenshot({path:path.join(salida,'h35-web-hacia-android.png'),fullPage:true});
   fs.writeFileSync(path.join(privado,'enlace-android.json'),JSON.stringify({uri}));
   console.log('Enlace del correo abre la web y ofrece continuar en Android; el token se retira de la URL.');
  } else {
   const datos=JSON.parse(fs.readFileSync(path.join(privado,'cuenta-movil.json'),'utf8'));
   const api=await context.request.get('http://127.0.0.1:18083/api/objetos/'+datos.objetoPublico);if(api.status()!==200)throw new Error('Publicación ausente');const objeto=await api.json();
   await page.goto('http://127.0.0.1:15176/objeto/'+datos.objetoPublico);await page.getByText(objeto.nombre,{exact:true}).first().waitFor();
   if(await page.evaluate(()=>localStorage.getItem('token'))!==null)throw new Error('La prueba pública tiene sesión');
   await page.screenshot({path:path.join(salida,'h39-web-publica.png'),fullPage:true});
   const retirada=await context.request.delete('http://127.0.0.1:18083/api/objetos/'+datos.objetoPublico,{headers:{Authorization:'Bearer '+datos.tokenUsuario}});if(retirada.status()!==204)throw new Error('No retirado');
   await page.reload();await page.getByText('Objeto no encontrado',{exact:true}).waitFor();await page.screenshot({path:path.join(salida,'h39-web-retirada.png'),fullPage:true});
   const reporte=path.join(salida,'api-14-historias.json');const evidencia=JSON.parse(fs.readFileSync(reporte,'utf8'));evidencia.historias.H39.navegadorPendiente=false;evidencia.historias.H39.publicacionYRetiradaVerificadasEnWeb=true;fs.writeFileSync(reporte,JSON.stringify(evidencia,null,2));
   console.log('H39: publicación pública sin sesión y retirada comprobadas en navegador real.');
  }
 } finally {await browser.close()}
})().catch(e=>{console.error('Falló la comprobación web:',e.name);process.exit(1)});
