"""Las horas que se imprimen para personas (etiquetas, constancias y reportes) van en hora de Lima.

Django sigue trabajando en UTC (TIME_ZONE = "UTC") para que la API y la base no cambien.
"""
from datetime import date, datetime, timezone as dt_timezone
from unittest import mock

import bcrypt
from rest_framework.test import APITestCase

from . import custodia, disposicion, reclamos_custodia
from .models import Categoria, Objeto, Usuario
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
