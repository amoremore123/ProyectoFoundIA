import sys
sys.path.insert(0,'qa/redisenio');from verificar_interfaz import *
assert adb('shell','getprop','debug.foundia.qa_room').decode().strip()=='20261027'
resultados=json.loads((OUT/'recorrido.json').read_text(encoding='utf-8'))
abrir();esperar('Hola, Ana');foto('05-inicio-con-sesion')
tap('Perfil');esperar('Mi perfil');foto('06-perfil-claro')
tap('Apariencia',True);esperar('A tu manera');tap('Oscuro');foto('07-apariencia-oscura');tap('Volver');abrir();esperar('Hola, Ana');foto('08-inicio-oscuro');resultados.append('El tema oscuro y la sesión persisten tras detener y reabrir')
tap('Buscar');esperar('¿Qué estás buscando?');foto('09-busqueda-oscura');campos(['visual']);esperar('Objeto visual QA perdido');foto('10-resultados-reales')
tap('Objeto visual QA perdido');esperar('Los detalles importan');foto('11-detalle-con-foto');tap('Comparar objetos →',True);esperar('DE SIMILITUD');foto('12-comparacion-real');resultados.append('Búsqueda, fotografías y comparación con criterios reales')
adb('shell','input','keyevent','4');adb('shell','input','keyevent','4');tap('Avisos');esperar('Tus novedades');foto('13-avisos-reales');resultados.append('Notificaciones consultadas en la API real')
(OUT/'recorrido.json').write_text(json.dumps(resultados,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(resultados,ensure_ascii=False),flush=True)
