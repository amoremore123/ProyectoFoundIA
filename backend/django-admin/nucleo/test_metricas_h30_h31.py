"""H30/H31: tiempo de recuperación y rankings verificados contra datos conocidos (SQLite temporal)."""
from datetime import date, datetime, timedelta, timezone

from rest_framework.test import APITestCase

from .models import Categoria, Objeto, Usuario
from .tests import token_de


def utc(dia, hora=12):
    return datetime(2026, 10, dia, hora, tzinfo=timezone.utc)


class MetricasH30H31Tests(APITestCase):
    def setUp(self):
        self.admin = Usuario.objects.create(nombre="Admin", apellido="QA", correo="admin@qa.invalid",
                                            password="no-se-usa", rol="ADMIN")
        self.usuario = Usuario.objects.create(nombre="Ana", apellido="QA", correo="ana@qa.invalid",
                                              password="no-se-usa")
        self.mochilas = Categoria.objects.create(nombre="Mochilas")
        self.celulares = Categoria.objects.create(nombre="Celulares")
        self.llaves = Categoria.objects.create(nombre="Llaves")

    def crear(self, tipo="PERDIDO", estado="ACTIVO", dia=5, categoria=None, ubicacion="Biblioteca", **extra):
        return Objeto.objects.create(usuario=self.usuario, categoria=categoria or self.mochilas,
                                      nombre="Objeto QA", descripcion="Dato conocido", tipo=tipo, estado=estado,
                                      ubicacion=ubicacion, fecha_objeto=date(2026, 10, dia),
                                      fecha_publicacion=utc(dia), **extra)

    def consultar(self, **filtros):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.admin)}")
        respuesta = self.client.get("/api/admin/dashboard/", filtros)
        self.assertEqual(respuesta.status_code, 200, respuesta.data)
        return respuesta.data

    # ---------------- H30 ----------------

    def test_h30_promedio_mediana_y_extremos_en_horas_y_dias(self):
        self.crear(estado="RECUPERADO", dia=1, fecha_recuperacion=utc(3))          # 48 h
        self.crear(estado="RECUPERADO", dia=2, fecha_recuperacion=utc(2, 18))      # 6 h
        self.crear(estado="ACTIVO", dia=4)                                         # aún perdido
        self.crear(tipo="ENCONTRADO", estado="RECUPERADO", dia=4, fecha_recuperacion=utc(5))  # no es perdido

        r = self.consultar()["recuperacion"]
        self.assertEqual(r, {"promedioHoras": 27.0, "promedioDias": 1.1, "medianaHoras": 27.0,
                             "minimoHoras": 6.0, "maximoHoras": 48.0, "muestra": 2,
                             "sinFecha": 0, "fechasIncoherentes": 0})

    def test_h30_no_inventa_fechas_ni_cuenta_datos_incoherentes_u_ocultos(self):
        self.crear(estado="RECUPERADO", dia=1, fecha_recuperacion=utc(1, 22))      # 10 h
        self.crear(estado="RECUPERADO", dia=2)                                     # antiguo, sin fecha
        self.crear(estado="RECUPERADO", dia=6, fecha_recuperacion=utc(5))          # antes de publicarse
        self.crear(estado="OCULTO", dia=3, fecha_recuperacion=utc(4))              # oculto: fuera

        r = self.consultar()["recuperacion"]
        self.assertEqual(r["muestra"], 1)
        self.assertEqual(r["promedioHoras"], 10.0)
        self.assertEqual(r["sinFecha"], 1)
        self.assertEqual(r["fechasIncoherentes"], 1)

    def test_h30_sin_recuperaciones_devuelve_nulos_y_no_cero(self):
        self.crear(estado="ACTIVO")
        r = self.consultar()["recuperacion"]
        self.assertIsNone(r["promedioHoras"])
        self.assertIsNone(r["medianaHoras"])
        self.assertEqual(r["muestra"], 0)

    def test_h30_respeta_el_rango_de_publicacion(self):
        self.crear(estado="RECUPERADO", dia=1, fecha_recuperacion=utc(1) + timedelta(hours=2))
        self.crear(estado="RECUPERADO", dia=10, fecha_recuperacion=utc(10) + timedelta(hours=20))
        r = self.consultar(desde="2026-10-05", hasta="2026-10-31")["recuperacion"]
        self.assertEqual((r["muestra"], r["promedioHoras"]), (1, 20.0))

    # ---------------- H31 ----------------

    def test_h31_categorias_ordenadas_con_desglose_y_desempate_por_nombre(self):
        for _ in range(3):
            self.crear(categoria=self.celulares)
        self.crear(categoria=self.mochilas, tipo="ENCONTRADO")
        self.crear(categoria=self.mochilas, estado="RECUPERADO")
        self.crear(categoria=self.llaves)
        self.crear(categoria=self.llaves, tipo="ENCONTRADO")
        self.crear(categoria=self.llaves, estado="ELIMINADO")  # no visible

        top = self.consultar()["categoriasTop"]
        self.assertEqual([(c["nombre"], c["total"]) for c in top],
                         [("Celulares", 3), ("Llaves", 2), ("Mochilas", 2)])
        self.assertEqual(top[2], {"id": self.mochilas.id, "nombre": "Mochilas", "total": 2,
                                  "perdidos": 1, "encontrados": 1})

    def test_h31_ubicaciones_agrupan_variantes_de_escritura(self):
        self.crear(ubicacion="Biblioteca")
        self.crear(ubicacion=" biblioteca ")
        self.crear(ubicacion="BIBLIOTECA", tipo="ENCONTRADO")
        self.crear(ubicacion="Pabellón  A")
        self.crear(ubicacion="pabellón a")
        self.crear(ubicacion="Cafetería")
        self.crear(ubicacion="")
        self.crear(ubicacion=None)

        datos = self.consultar()
        self.assertEqual(datos["ubicacionesTop"], [
            {"nombre": "Biblioteca", "total": 3, "perdidos": 2, "encontrados": 1},
            {"nombre": "Pabellón A", "total": 2, "perdidos": 2, "encontrados": 0},
            {"nombre": "Cafetería", "total": 1, "perdidos": 1, "encontrados": 0},
        ])
        self.assertEqual(datos["sinUbicacion"], 2)

    def test_h31_muestra_como_maximo_cinco_y_respeta_el_rango(self):
        for indice in range(7):
            self.crear(ubicacion=f"Aula {indice}", dia=5)
        self.crear(ubicacion="Patio", dia=20)
        datos = self.consultar(hasta="2026-10-10")
        self.assertEqual(len(datos["ubicacionesTop"]), 5)
        self.assertNotIn("Patio", [u["nombre"] for u in datos["ubicacionesTop"]])

    def test_sin_datos_devuelve_listas_vacias(self):
        datos = self.consultar(desde="2027-01-01")
        self.assertEqual(datos["categoriasTop"], [])
        self.assertEqual(datos["ubicacionesTop"], [])
        self.assertEqual(datos["sinUbicacion"], 0)

    def test_no_expone_datos_de_personas(self):
        self.crear(estado="RECUPERADO", dia=1, fecha_recuperacion=utc(2))
        texto = str(self.consultar())
        self.assertNotIn(self.usuario.correo, texto)
        self.assertNotIn("usuario", texto.lower().replace("usuarios", ""))

    def test_solo_administrador(self):
        self.assertEqual(self.client.get("/api/admin/dashboard/").status_code, 401)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.usuario)}")
        self.assertEqual(self.client.get("/api/admin/dashboard/").status_code, 403)
