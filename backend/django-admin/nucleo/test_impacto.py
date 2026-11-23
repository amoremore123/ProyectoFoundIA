"""Métricas de impacto: devueltos por mes y por vía, sin datos personales."""
from datetime import date, timedelta

import bcrypt
from django.utils import timezone
from rest_framework.test import APITestCase

from .metricas import _restar_meses, metricas_impacto
from .models import Categoria, Custodia, Objeto, Usuario
from .tests import PASSWORD, token_de


class ImpactoTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.admin = Usuario.objects.create(nombre="Admin", apellido="Impacto", correo="admin-impacto@prueba.invalid",
                                            password=hash_, rol="ADMIN", estado="ACTIVO")
        self.biblioteca = Usuario.objects.create(nombre="Beto", apellido="Biblio", correo="bib-impacto@prueba.invalid",
                                                 password=hash_, rol="BIBLIOTECA", estado="ACTIVO")
        self.ana = Usuario.objects.create(nombre="Ana", apellido="Secreta", correo="ana-impacto@prueba.invalid",
                                          password=hash_, rol="USUARIO", estado="ACTIVO")
        self.categoria = Categoria.objects.create(nombre="Impacto")

    def objeto(self, tipo, estado="ACTIVO", recuperado_hace=None):
        o = Objeto.objects.create(usuario=self.ana, categoria=self.categoria, nombre=f"{tipo} {estado}",
                                  descripcion="x", ubicacion="Biblioteca", fecha_objeto=date.today(), tipo=tipo, estado=estado)
        if recuperado_hace is not None:
            o.fecha_publicacion = timezone.now() - timedelta(days=recuperado_hace + 2)
            o.fecha_recuperacion = timezone.now() - timedelta(days=recuperado_hace)
            o.save()
        return o

    def test_cuenta_devueltos_por_via_y_por_mes(self):
        self.objeto("PERDIDO", "RECUPERADO", recuperado_hace=1)
        en_biblioteca = self.objeto("ENCONTRADO", "RECUPERADO", recuperado_hace=2)
        Custodia.objects.create(objeto=en_biblioteca, codigo="FND-2026-9001", token_qr="t", recibido_por=self.biblioteca, estado="RETIRADO")
        self.objeto("PERDIDO", "ACTIVO")
        en_custodia = self.objeto("ENCONTRADO", "EN_CUSTODIA")
        Custodia.objects.create(objeto=en_custodia, codigo="FND-2026-9002", token_qr="t2", recibido_por=self.biblioteca, estado="EN_CUSTODIA")

        datos = metricas_impacto()
        self.assertEqual(datos["devueltos"], 2)
        self.assertEqual(datos["devueltosUltimos30Dias"], 2)
        self.assertEqual(datos["enBiblioteca"], 1)
        self.assertEqual(len(datos["porMes"]), 6)
        total_meses = sum(m["total"] for m in datos["porMes"])
        self.assertEqual(total_meses, 2)
        self.assertEqual(sum(m["biblioteca"] for m in datos["porMes"]), 1)
        self.assertEqual(sum(m["personas"] for m in datos["porMes"]), 1)
        self.assertEqual(datos["perdidosRecuperadosPorcentaje"], 50.0)
        self.assertEqual(datos["custodia"], {"enCustodia": 1, "entregadas": 1, "dispuestas": 0})
        self.assertAlmostEqual(datos["tiempoPromedioDias"], 2.0, places=1)

    def test_sin_datos_no_inventa_porcentajes(self):
        datos = metricas_impacto()
        self.assertEqual(datos["devueltos"], 0)
        self.assertIsNone(datos["perdidosRecuperadosPorcentaje"])
        self.assertIsNone(datos["tiempoPromedioDias"])
        self.assertTrue(all(m["total"] == 0 for m in datos["porMes"]))

    def test_meses_en_orden_y_cambio_de_anio(self):
        datos = metricas_impacto(hoy=date(2026, 2, 15))
        self.assertEqual([m["mes"] for m in datos["porMes"]], ["2025-09", "2025-10", "2025-11", "2025-12", "2026-01", "2026-02"])
        self.assertEqual(_restar_meses(date(2026, 1, 1), 1), date(2025, 12, 1))

    def test_solo_admin_y_sin_datos_personales(self):
        self.objeto("PERDIDO", "RECUPERADO", recuperado_hace=1)
        self.assertEqual(self.client.get("/api/admin/metricas/impacto/").status_code, 401)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.biblioteca)}")
        self.assertEqual(self.client.get("/api/admin/metricas/impacto/").status_code, 403)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.admin)}")
        r = self.client.get("/api/admin/metricas/impacto/")
        self.assertEqual(r.status_code, 200)
        self.assertNotIn("Secreta", str(r.content))
        self.assertNotIn("ana-impacto", str(r.content))
