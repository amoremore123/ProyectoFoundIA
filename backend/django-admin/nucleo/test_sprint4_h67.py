"""H67: métricas de alcance por origen de campaña (visitas y registros que cuenta Spring)."""
from datetime import datetime, timedelta, timezone as dt_timezone
from unittest import mock

import bcrypt
from rest_framework.test import APITestCase

from .models import AlcanceCampana, Campana, Usuario, hoy_en_lima
from .tests import PASSWORD, token_de

RUTA = "/api/admin/campanas/"


class AlcanceCampanasTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.admin = Usuario.objects.create(nombre="Admin", apellido="QA", correo="admin-h67@prueba.invalid",
                                            password=hash_, rol="ADMIN", estado="ACTIVO")
        self.ana = Usuario.objects.create(nombre="Ana", apellido="QA", correo="ana-h67@prueba.invalid",
                                          password=hash_, rol="USUARIO", estado="ACTIVO")
        self.hoy = hoy_en_lima()
        self.semana = Campana.objects.create(
            nombre="Semana de objetos perdidos", origen="semana-perdidos-2026", destino="/explorar",
            fecha_inicio=self.hoy - timedelta(days=2), fecha_fin=self.hoy + timedelta(days=4), creada_por=self.admin)
        self.feria = Campana.objects.create(
            nombre="Feria", origen="feria-2026", destino="/",
            fecha_inicio=self.hoy - timedelta(days=10), fecha_fin=self.hoy - timedelta(days=5), creada_por=self.admin)
        self.n = 0

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def evento(self, campana, tipo, cuando_utc):
        self.n += 1
        AlcanceCampana.objects.create(campana=campana, tipo=tipo, fecha=cuando_utc,
                                      visita_id=f"visita-{self.n:04d}" if tipo == "VISITA" else None)

    def dia_lima(self, dias, hora=12):
        """Mediodía de Lima del día hoy+dias, en UTC."""
        local = datetime.combine(self.hoy + timedelta(days=dias), datetime.min.time()) + timedelta(hours=hora)
        return local.replace(tzinfo=dt_timezone(timedelta(hours=-5))).astimezone(dt_timezone.utc)

    def test_tablero_por_campana_con_conversion_y_total(self):
        for _ in range(4):
            self.evento(self.semana, "VISITA", self.dia_lima(-1))
        self.evento(self.semana, "REGISTRO", self.dia_lima(-1))
        self.evento(self.feria, "VISITA", self.dia_lima(-6))
        self.como(self.admin)
        r = self.client.get(f"{RUTA}alcance/")
        self.assertEqual(r.status_code, 200, r.content)
        self.assertEqual(r.data["hasta"], str(self.hoy))
        self.assertEqual(r.data["desde"], str(self.hoy - timedelta(days=29)))
        filas = {f["origen"]: f for f in r.data["campanas"]}
        self.assertEqual(filas["semana-perdidos-2026"]["visitas"], 4)
        self.assertEqual(filas["semana-perdidos-2026"]["registros"], 1)
        self.assertEqual(filas["semana-perdidos-2026"]["conversion"], 25.0)
        self.assertEqual(filas["semana-perdidos-2026"]["estado"], "ACTIVA")
        self.assertEqual(filas["feria-2026"]["visitas"], 1)
        self.assertEqual(filas["feria-2026"]["conversion"], 0.0)
        self.assertEqual(r.data["total"], {"visitas": 5, "registros": 1})
        # La que tiene más visitas va primero
        self.assertEqual(r.data["campanas"][0]["origen"], "semana-perdidos-2026")

    def test_tablero_respeta_el_rango(self):
        self.evento(self.semana, "VISITA", self.dia_lima(-2))
        self.evento(self.semana, "VISITA", self.dia_lima(0))
        self.como(self.admin)
        r = self.client.get(f"{RUTA}alcance/?desde={self.hoy}&hasta={self.hoy}")
        fila = [f for f in r.data["campanas"] if f["id"] == self.semana.id][0]
        self.assertEqual(fila["visitas"], 1)

    def test_detalle_por_dia_con_ceros_y_rango_por_defecto(self):
        self.evento(self.semana, "VISITA", self.dia_lima(-2))
        self.evento(self.semana, "VISITA", self.dia_lima(-2))
        self.evento(self.semana, "REGISTRO", self.dia_lima(0))
        self.evento(self.semana, "VISITA", self.dia_lima(0))
        self.evento(self.feria, "VISITA", self.dia_lima(0))  # otra campaña no se mezcla
        self.como(self.admin)
        r = self.client.get(f"{RUTA}{self.semana.id}/alcance/")
        self.assertEqual(r.status_code, 200, r.content)
        # Por defecto: de su inicio a hoy (todavía no termina)
        self.assertEqual(r.data["desde"], str(self.hoy - timedelta(days=2)))
        self.assertEqual(r.data["hasta"], str(self.hoy))
        self.assertEqual(r.data["porDia"], [
            {"fecha": str(self.hoy - timedelta(days=2)), "visitas": 2, "registros": 0},
            {"fecha": str(self.hoy - timedelta(days=1)), "visitas": 0, "registros": 0},
            {"fecha": str(self.hoy), "visitas": 1, "registros": 1},
        ])
        self.assertEqual((r.data["visitas"], r.data["registros"], r.data["conversion"]), (3, 1, 33.3))
        # Una campaña terminada: por defecto hasta su fin
        r = self.client.get(f"{RUTA}{self.feria.id}/alcance/")
        self.assertEqual(r.data["hasta"], str(self.feria.fecha_fin))

    def test_los_dias_son_de_lima_y_no_de_utc(self):
        # Las 22:00 de ayer en Lima ya son hoy en UTC: debe contar para ayer
        self.evento(self.semana, "VISITA", self.dia_lima(-1, hora=22))
        self.como(self.admin)
        r = self.client.get(f"{RUTA}{self.semana.id}/alcance/")
        dias = {d["fecha"]: d["visitas"] for d in r.data["porDia"]}
        self.assertEqual(dias[str(self.hoy - timedelta(days=1))], 1)
        self.assertEqual(dias[str(self.hoy)], 0)

    def test_fechas_invalidas(self):
        self.como(self.admin)
        for consulta in ("?desde=23/11/2026", "?hasta=2026-13-01", "?desde=2026-11-20&hasta=2026-11-10",
                         "?desde=2025-01-01&hasta=2026-12-31"):
            with self.subTest(consulta=consulta):
                self.assertEqual(self.client.get(f"{RUTA}alcance/{consulta}").status_code, 400)
                self.assertEqual(self.client.get(f"{RUTA}{self.semana.id}/alcance/{consulta}").status_code, 400)
        self.assertEqual(self.client.get(f"{RUTA}alcance/?desde=2026-01-01&hasta=2027-01-01").status_code, 200)

    def test_permisos_y_no_existe(self):
        self.assertEqual(self.client.get(f"{RUTA}alcance/").status_code, 401)
        self.como(self.ana)
        self.assertEqual(self.client.get(f"{RUTA}alcance/").status_code, 403)
        self.assertEqual(self.client.get(f"{RUTA}{self.semana.id}/alcance/").status_code, 403)
        self.como(self.admin)
        self.assertEqual(self.client.get(f"{RUTA}999999/alcance/").status_code, 404)

    def test_la_respuesta_no_trae_datos_personales(self):
        self.evento(self.semana, "VISITA", self.dia_lima(0))
        self.como(self.admin)
        texto = self.client.get(f"{RUTA}{self.semana.id}/alcance/").content.decode()
        self.assertNotIn("visita-0001", texto)
        self.assertNotIn("correo", texto)

    def test_rango_por_defecto_usa_el_dia_de_lima(self):
        instante = datetime(2026, 11, 25, 3, 0, tzinfo=dt_timezone.utc)  # 22:00 del 24 en Lima
        self.como(self.admin)
        with mock.patch("django.utils.timezone.now", return_value=instante):
            r = self.client.get(f"{RUTA}alcance/")
        self.assertEqual(r.data["hasta"], "2026-11-24")
