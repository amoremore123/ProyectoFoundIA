"""Las horas que se imprimen para personas (etiquetas, constancias y reportes) van en hora de Lima.

Django sigue trabajando en UTC (TIME_ZONE = "UTC") para que la API y la base no cambien.
"""
from datetime import date, datetime, timezone as dt_timezone
from unittest import mock

import bcrypt
from rest_framework.test import APITestCase

from . import custodia, disposicion, reclamos_custodia
from .models import Categoria, Custodia, Objeto, Reclamo, Usuario
from .tests import PASSWORD, token_de

# 17:46 en UTC son las 12:46 en Lima (UTC-5, sin horario de verano)
INSTANTE = datetime(2026, 11, 23, 17, 46, tzinfo=dt_timezone.utc)


class HoraDeLimaTests(APITestCase):
    def test_las_constancias_y_etiquetas_imprimen_hora_de_lima(self):
        self.assertEqual(custodia._fecha_local(INSTANTE), "23/11/2026 12:46")
        self.assertEqual(disposicion._fecha_local(INSTANTE), "23/11/2026 12:46")
        self.assertEqual(reclamos_custodia._fecha(INSTANTE), "23/11/2026 12:46")

    def test_el_cambio_de_dia_es_el_de_lima(self):
        # 03:30 UTC del 24 todavía son las 22:30 del 23 en Lima
        madrugada_utc = datetime(2026, 11, 24, 3, 30, tzinfo=dt_timezone.utc)
        self.assertEqual(custodia._fecha_local(madrugada_utc), "23/11/2026 22:30")

    def test_sin_fecha_y_fecha_sin_zona_no_cambian(self):
        self.assertEqual(custodia._fecha_local(None), "—")
        self.assertEqual(custodia._fecha_local(datetime(2026, 11, 23, 9, 5)), "23/11/2026 09:05")


class CodigoDeCustodiaTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.biblioteca = Usuario.objects.create(
            nombre="Beto", apellido="Biblioteca", correo="biblioteca-lima@prueba.invalid",
            password=hash_, rol="BIBLIOTECA", estado="ACTIVO")
        ana = Usuario.objects.create(nombre="Ana", apellido="QA", correo="ana-lima@prueba.invalid",
                                     password=hash_, rol="USUARIO", estado="ACTIVO")
        categoria = Categoria.objects.create(nombre="Mochilas Lima")
        self.objeto = Objeto.objects.create(
            usuario=ana, categoria=categoria, nombre="Mochila azul", descripcion="Mochila azul",
            ubicacion="Biblioteca", fecha_objeto=date(2026, 12, 31), tipo="ENCONTRADO", estado="ACTIVO")

    def test_el_anio_del_codigo_es_el_de_lima(self):
        # 31/12/2026 a las 23:00 en Lima ya es 01/01/2027 en UTC: el código debe ser de 2026
        fin_de_anio = datetime(2027, 1, 1, 4, 0, tzinfo=dt_timezone.utc)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.biblioteca)}")
        with mock.patch("django.utils.timezone.now", return_value=fin_de_anio):
            r = self.client.post("/api/admin/custodias/", {"objetoId": self.objeto.id}, format="json")
        self.assertEqual(r.status_code, 201, r.data)
        self.assertEqual(r.data["codigo"], "FND-2026-0001")


class FiltrosPorDiaDeLimaTests(APITestCase):
    """Los filtros «desde/hasta» cuentan días de Lima: algo registrado a las 20:00 del 23 en Lima (ya 24 en UTC)
    es del 23."""

    NOCHE_DEL_23 = datetime(2026, 11, 24, 1, 0, tzinfo=dt_timezone.utc)   # 20:00 del 23 en Lima
    MANANA_DEL_24 = datetime(2026, 11, 24, 14, 0, tzinfo=dt_timezone.utc)  # 09:00 del 24 en Lima

    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.admin = Usuario.objects.create(nombre="Ada", apellido="QA", correo="admin-dias@prueba.invalid",
                                            password=hash_, rol="ADMIN", estado="ACTIVO")
        self.ana = Usuario.objects.create(nombre="Ana", apellido="QA", correo="ana-dias@prueba.invalid",
                                          password=hash_, rol="USUARIO", estado="ACTIVO")
        cat = Categoria.objects.create(nombre="Mochilas días")
        self.noche, self.manana = [Objeto.objects.create(
            usuario=self.ana, categoria=cat, nombre=nombre, descripcion=nombre, ubicacion="Biblioteca",
            fecha_objeto=date(2026, 11, 23), tipo="PERDIDO", estado="ACTIVO", fecha_publicacion=cuando)
            for nombre, cuando in (("Noche", self.NOCHE_DEL_23), ("Mañana", self.MANANA_DEL_24))]
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.admin)}")

    def test_tablero(self):
        dia23 = self.client.get("/api/admin/dashboard/?desde=2026-11-23&hasta=2026-11-23").data
        self.assertEqual(dia23["objetos"]["perdidos"], 1)          # la de las 20:00 del 23
        self.assertEqual(dia23["rango"]["zonaHoraria"], "America/Lima")
        dia24 = self.client.get("/api/admin/dashboard/?desde=2026-11-24&hasta=2026-11-24").data
        self.assertEqual(dia24["objetos"]["perdidos"], 1)          # solo la de la mañana del 24
        ambos = self.client.get("/api/admin/dashboard/?desde=2026-11-23&hasta=2026-11-24").data
        self.assertEqual(ambos["objetos"]["perdidos"], 2)

    def test_custodias(self):
        for objeto, cuando, codigo in ((self.noche, self.NOCHE_DEL_23, "FND-2026-0901"),
                                       (self.manana, self.MANANA_DEL_24, "FND-2026-0902")):
            Custodia.objects.create(objeto=objeto, codigo=codigo, token_qr="t", recibido_por=self.admin,
                                    fecha_recepcion=cuando)
        r = self.client.get("/api/admin/custodias/?desde=2026-11-23&hasta=2026-11-23")
        self.assertEqual(r.status_code, 200, r.data)
        self.assertEqual([c["codigo"] for c in r.data["results"]], ["FND-2026-0901"])
        r = self.client.get("/api/admin/custodias/?desde=2026-11-24")
        self.assertEqual([c["codigo"] for c in r.data["results"]], ["FND-2026-0902"])

    def test_reclamos(self):
        # La lista de reclamos del panel solo muestra los de objetos en custodia (H43)
        for i, (objeto, cuando) in enumerate(((self.noche, self.NOCHE_DEL_23), (self.manana, self.MANANA_DEL_24))):
            Custodia.objects.create(objeto=objeto, codigo=f"FND-2026-091{i}", token_qr="t", recibido_por=self.admin)
            Reclamo.objects.create(objeto=objeto, usuario=self.ana, estado="PENDIENTE", fecha_reclamo=cuando)
        r = self.client.get("/api/admin/reclamos/?hasta=2026-11-23")
        self.assertEqual(r.status_code, 200, r.data)
        filas = r.data["results"] if isinstance(r.data, dict) else r.data
        self.assertEqual([f["objeto"]["id"] if isinstance(f.get("objeto"), dict) else f.get("objetoId", f.get("objeto"))
                          for f in filas], [self.noche.id])

