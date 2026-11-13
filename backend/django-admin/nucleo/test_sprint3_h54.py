"""H54: disposición final de objetos no reclamados con acta, permisos, plazo y repetición."""
import os
from datetime import date, timedelta
from unittest import mock

import bcrypt
from django.utils import timezone
from rest_framework.test import APITestCase

from .models import Categoria, Custodia, Disposicion, Notificacion, Objeto, Reclamo, Usuario
from .tests import PASSWORD, token_de


@mock.patch.dict(os.environ, {"CUSTODIA_PLAZO_DIAS": "30"})
class DisposicionTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.biblioteca = Usuario.objects.create(
            nombre="Beto", apellido="Biblioteca", correo="biblio-h54@prueba.invalid",
            password=hash_, rol="BIBLIOTECA", estado="ACTIVO")
        self.ana = Usuario.objects.create(
            nombre="Ana", apellido="Pérez", correo="ana-h54@prueba.invalid",
            password=hash_, rol="USUARIO", estado="ACTIVO")
        self.luis = Usuario.objects.create(
            nombre="Luis", apellido="Rojas", correo="luis-h54@prueba.invalid",
            password=hash_, rol="USUARIO", estado="ACTIVO")
        self.categoria = Categoria.objects.create(nombre="Mochilas H54")
        self.vencida = self.custodia("Mochila azul", dias=45, codigo="FND-2026-0101")
        self.reciente = self.custodia("Laptop gris", dias=5, codigo="FND-2026-0102")

    def custodia(self, nombre, dias, codigo):
        objeto = Objeto.objects.create(
            usuario=self.ana, categoria=self.categoria, nombre=nombre, descripcion=f"Descripción de {nombre}",
            ubicacion="Biblioteca", fecha_objeto=date.today(), tipo="ENCONTRADO", estado="EN_CUSTODIA")
        return Custodia.objects.create(
            objeto=objeto, codigo=codigo, token_qr="x", recibido_por=self.biblioteca,
            fecha_recepcion=timezone.now() - timedelta(days=dias), estado="EN_CUSTODIA")

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def disponer(self, custodia, **extra):
        cuerpo = {"destino": "DONADO", "motivo": "Sin reclamos tras el plazo", "confirmar": True} | extra
        return self.client.post(f"/api/admin/custodias/{custodia.id}/disponer/", cuerpo, format="json")

    def test_permisos(self):
        self.assertEqual(self.client.get("/api/admin/custodias/vencidas/").status_code, 401)
        self.como(self.ana)
        self.assertEqual(self.client.get("/api/admin/custodias/vencidas/").status_code, 403)
        self.assertEqual(self.disponer(self.vencida).status_code, 403)

    def test_lista_solo_las_vencidas_con_el_plazo_configurado(self):
        self.como(self.biblioteca)
        r = self.client.get("/api/admin/custodias/vencidas/")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data["plazoDias"], 30)
        self.assertEqual([c["codigo"] for c in r.data["resultados"]], ["FND-2026-0101"])
        self.assertGreaterEqual(r.data["resultados"][0]["diasEnCustodia"], 45)
        self.assertNotIn("tokenQr", r.data["resultados"][0])

    def test_dispone_con_acta_responsable_y_fecha_sin_borrar(self):
        pendiente = Reclamo.objects.create(objeto=self.vencida.objeto, usuario=self.luis, estado="PENDIENTE")
        self.como(self.biblioteca)
        r = self.disponer(self.vencida, destino="DESCARTADO", motivo="Objeto deteriorado, sin reclamos")
        self.assertEqual(r.status_code, 201, r.data)
        self.assertEqual(r.data["estado"], "DISPUESTO")
        self.assertEqual(r.data["disposicion"]["codigoActa"], "ACTA-FND-2026-0101")
        self.assertEqual(r.data["disposicion"]["responsable"], "Beto Biblioteca")

        self.vencida.refresh_from_db()
        self.vencida.objeto.refresh_from_db()
        self.assertEqual(self.vencida.estado, "DISPUESTO")
        self.assertEqual(self.vencida.objeto.estado, "OCULTO")
        self.assertTrue(Objeto.objects.filter(pk=self.vencida.objeto.pk).exists())
        d = Disposicion.objects.get(custodia=self.vencida)
        self.assertEqual((d.destino, d.plazo_dias, d.responsable_id), ("DESCARTADO", 30, self.biblioteca.id))

        pendiente.refresh_from_db()
        self.assertEqual(pendiente.estado, "RECHAZADO")
        self.assertTrue(Notificacion.objects.filter(usuario=self.luis, titulo="Reclamo cerrado").exists())

        acta = self.client.get(f"/api/admin/custodias/{self.vencida.id}/acta/")
        self.assertEqual(acta.status_code, 200)
        self.assertEqual(acta["Content-Type"], "application/pdf")
        self.assertTrue(acta.content.startswith(b"%PDF"))
        self.assertIn("ACTA-FND-2026-0101.pdf", acta["Content-Disposition"])

    def test_no_dispone_si_el_plazo_no_vence(self):
        self.como(self.biblioteca)
        r = self.disponer(self.reciente)
        self.assertEqual(r.status_code, 400)
        self.assertIn("faltan", r.data["detail"])
        self.assertFalse(Disposicion.objects.exists())

    def test_repetir_responde_409_y_no_duplica(self):
        self.como(self.biblioteca)
        self.assertEqual(self.disponer(self.vencida).status_code, 201)
        self.assertEqual(self.disponer(self.vencida).status_code, 409)
        self.assertEqual(Disposicion.objects.count(), 1)

    def test_reclamo_aceptado_bloquea_la_disposicion(self):
        Reclamo.objects.create(objeto=self.vencida.objeto, usuario=self.luis, estado="ACEPTADO")
        self.como(self.biblioteca)
        self.assertTrue(self.client.get("/api/admin/custodias/vencidas/").data["resultados"][0]["bloqueada"])
        self.assertEqual(self.disponer(self.vencida).status_code, 409)

    def test_exige_confirmacion_destino_y_motivo(self):
        self.como(self.biblioteca)
        self.assertEqual(self.disponer(self.vencida, confirmar=False).status_code, 400)
        self.assertEqual(self.disponer(self.vencida, destino="VENDIDO").status_code, 400)
        self.assertEqual(self.disponer(self.vencida, motivo="corto").status_code, 400)
        self.assertEqual(self.disponer(self.vencida, responsable=1).status_code, 400)
        self.assertFalse(Disposicion.objects.exists())

    def test_acta_inexistente_responde_404(self):
        self.como(self.biblioteca)
        self.assertEqual(self.client.get(f"/api/admin/custodias/{self.reciente.id}/acta/").status_code, 404)
