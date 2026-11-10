"""H43: bandeja de reclamos de custodia, resolución y QR de retiro de un uso."""
from datetime import date, timedelta

import bcrypt
from django.utils import timezone
from rest_framework.test import APITestCase

from .custodia import firmar_token_custodia
from .models import Categoria, Custodia, Notificacion, Objeto, Reclamo, Usuario
from .tests import PASSWORD, token_de


class ReclamosCustodiaTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.admin = Usuario.objects.create(
            nombre="Admin", apellido="QA", correo="admin-h43@prueba.invalid",
            password=hash_, rol="ADMIN", estado="ACTIVO")
        self.biblioteca = Usuario.objects.create(
            nombre="Beto", apellido="Biblioteca", correo="biblioteca-h43@prueba.invalid",
            password=hash_, rol="BIBLIOTECA", estado="ACTIVO")
        self.ana = Usuario.objects.create(
            nombre="Ana", apellido="Pérez", correo="ana-h43@prueba.invalid",
            password=hash_, rol="USUARIO", estado="ACTIVO")
        self.luis = Usuario.objects.create(
            nombre="Luis", apellido="Gómez", correo="luis-h43@prueba.invalid",
            password=hash_, rol="USUARIO", estado="ACTIVO")
        self.categoria = Categoria.objects.create(nombre="Mochilas H43")
        self.objeto = self.crear_objeto("Mochila azul")
        self.custodia = self.poner_en_custodia(self.objeto)
        self.reclamo = self.crear_reclamo(self.objeto, self.ana)

    def crear_objeto(self, nombre):
        return Objeto.objects.create(
            usuario=self.luis, categoria=self.categoria, nombre=nombre,
            descripcion=f"Descripción de {nombre}", ubicacion="Biblioteca central",
            fecha_objeto=date.today(), tipo="ENCONTRADO", estado="ACTIVO")

    def poner_en_custodia(self, objeto):
        custodia = Custodia(
            objeto=objeto, codigo=f"FND-2026-{objeto.id:04d}",
            recibido_por=self.biblioteca, fecha_recepcion=timezone.now(),
            estado="EN_CUSTODIA")
        custodia.token_qr = firmar_token_custodia(custodia)
        custodia.save()
        objeto.estado = "EN_CUSTODIA"
        objeto.save(update_fields=["estado", "fecha_actualizacion"])
        return custodia

    def crear_reclamo(self, objeto, usuario):
        return Reclamo.objects.create(objeto=objeto, usuario=usuario)

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def resolver(self, reclamo, decision, motivo="Documento de identidad verificado"):
        return self.client.post(
            f"/api/admin/reclamos/{reclamo.id}/resolver/",
            {"decision": decision, "motivo": motivo}, format="json")

    def aceptar(self, reclamo):
        self.como(self.biblioteca)
        r = self.resolver(reclamo, "ACEPTADO")
        self.assertEqual(r.status_code, 200, r.data)
        reclamo.refresh_from_db()
        return r

    def test_bandeja_muestra_solo_reclamos_de_objetos_en_custodia(self):
        suelto = self.crear_objeto("Objeto sin custodia")
        self.crear_reclamo(suelto, self.luis)
        self.como(self.biblioteca)
        r = self.client.get("/api/admin/reclamos/")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data["count"], 1)
        self.assertEqual(r.data["results"][0]["id"], self.reclamo.id)
        self.assertEqual(r.data["results"][0]["objeto"]["id"], self.objeto.id)
        self.assertEqual(r.data["results"][0]["reclamante"]["nombre"], "Ana Pérez")
        self.assertEqual(r.data["results"][0]["estado"], "PENDIENTE")

    def test_bandeja_filtra_por_estado_texto_y_objeto(self):
        self.como(self.biblioteca)
        self.assertEqual(self.client.get("/api/admin/reclamos/", {"q": "Ana"}).data["count"], 1)
        self.assertEqual(self.client.get("/api/admin/reclamos/", {"q": "Mochila"}).data["count"], 1)
        self.assertEqual(self.client.get("/api/admin/reclamos/", {"q": "nadie"}).data["count"], 0)
        self.assertEqual(self.client.get(
            "/api/admin/reclamos/", {"objetoId": self.objeto.id}).data["count"], 1)
        self.assertEqual(self.client.get(
            "/api/admin/reclamos/", {"estado": "ACEPTADO"}).data["count"], 0)
        self.assertEqual(self.client.get(
            "/api/admin/reclamos/", {"estado": "OTRO"}).status_code, 400)

    def test_aceptar_genera_qr_de_siete_dias_y_avisa_al_reclamante(self):
        r = self.aceptar(self.reclamo)
        self.assertEqual(r.data["estado"], "ACEPTADO")
        self.assertTrue(r.data["qrRetiroVigente"])
        self.assertTrue(r.data["qrRetiroUrl"])
        self.assertIsNotNone(self.reclamo.qr_retiro_token)
        vigencia = self.reclamo.qr_retiro_expira - timezone.now()
        self.assertGreater(vigencia, timedelta(days=6, hours=23))
        self.assertLessEqual(vigencia, timedelta(days=7))
        self.assertEqual(self.reclamo.resuelto_por, self.biblioteca)
        self.assertIsNotNone(self.reclamo.fecha_resolucion)

        aviso = Notificacion.objects.get(usuario=self.ana, tipo="RECLAMO")
        self.assertIn("aceptado", aviso.titulo.lower())
        self.assertIn("7 días", aviso.mensaje)
        self.assertEqual(aviso.enlace, f"/objetos/{self.objeto.id}")

    def test_aceptar_rechaza_los_reclamos_rivales_y_los_avisa(self):
        rival = self.crear_reclamo(self.objeto, self.luis)
        self.aceptar(self.reclamo)
        rival.refresh_from_db()
        self.assertEqual(rival.estado, "RECHAZADO")
        self.assertIn("otro reclamo", rival.motivo_resolucion)
        self.assertEqual(rival.resuelto_por, self.biblioteca)
        self.assertIsNotNone(rival.fecha_resolucion)
        self.assertIsNone(rival.qr_retiro_token)

        aviso = Notificacion.objects.get(usuario=self.luis, tipo="RECLAMO")
        self.assertIn("rechazado", aviso.titulo.lower())
        self.assertIn("otro reclamo", aviso.mensaje)

    def test_no_se_resuelve_dos_veces(self):
        self.aceptar(self.reclamo)
        segundo = self.resolver(self.reclamo, "RECHAZADO")
        self.assertEqual(segundo.status_code, 409)
        self.assertIn("ya fue resuelto", segundo.data["detail"])

    def test_no_se_acepta_un_segundo_reclamo_para_el_mismo_objeto(self):
        otro = self.crear_reclamo(self.objeto, self.luis)
        otro.estado = "ACEPTADO"
        otro.save(update_fields=["estado"])
        self.como(self.biblioteca)
        r = self.resolver(self.reclamo, "ACEPTADO")
        self.assertEqual(r.status_code, 409)
        self.assertIn("otro reclamo aceptado", r.data["detail"])
        self.reclamo.refresh_from_db()
        self.assertEqual(self.reclamo.estado, "PENDIENTE")
        self.assertIsNone(self.reclamo.qr_retiro_token)

    def test_rechazar_exige_motivo_y_no_genera_qr(self):
        self.como(self.biblioteca)
        sin_motivo = self.client.post(
            f"/api/admin/reclamos/{self.reclamo.id}/resolver/",
            {"decision": "RECHAZADO", "motivo": "   "}, format="json")
        self.assertEqual(sin_motivo.status_code, 400)
        con_extra = self.client.post(
            f"/api/admin/reclamos/{self.reclamo.id}/resolver/",
            {"decision": "RECHAZADO", "motivo": "No coincide", "actor": 1}, format="json")
        self.assertEqual(con_extra.status_code, 400)

        r = self.resolver(self.reclamo, "RECHAZADO", "La descripción no coincide")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data["estado"], "RECHAZADO")
        self.assertIsNone(r.data["qrRetiroUrl"])
        self.reclamo.refresh_from_db()
        self.assertIsNone(self.reclamo.qr_retiro_token)
        aviso = Notificacion.objects.get(usuario=self.ana, tipo="RECLAMO")
        self.assertIn("La descripción no coincide", aviso.mensaje)

    def test_qr_pdf_verificacion_y_retiro_de_un_solo_uso(self):
        self.aceptar(self.reclamo)
        token = self.reclamo.qr_retiro_token
        self.como(self.biblioteca)

        pdf = self.client.get(f"/api/admin/reclamos/{self.reclamo.id}/qr-retiro/")
        self.assertEqual(pdf.status_code, 200)
        self.assertEqual(pdf["Content-Type"], "application/pdf")
        self.assertTrue(pdf.content.startswith(b"%PDF"))

        verificado = self.client.get("/api/admin/reclamos/verificar-qr/", {"token": token})
        self.assertEqual(verificado.status_code, 200)
        self.assertTrue(verificado.data["valido"])
        self.assertEqual(verificado.data["estadoQr"], "VIGENTE")
        self.assertEqual(verificado.data["reclamo"]["id"], self.reclamo.id)

        retiro = self.client.post("/api/admin/reclamos/retirar/", {"token": token}, format="json")
        self.assertEqual(retiro.status_code, 200, retiro.data)
        self.assertEqual(retiro.data["estado"], "RETIRADO")

        self.reclamo.refresh_from_db()
        self.custodia.refresh_from_db()
        self.objeto.refresh_from_db()
        self.assertEqual(self.reclamo.estado, "RETIRADO")
        self.assertIsNotNone(self.reclamo.qr_retiro_usado_en)
        self.assertEqual(self.custodia.estado, "RETIRADO")
        self.assertEqual(self.objeto.estado, "RECUPERADO")
        self.assertIsNotNone(self.objeto.fecha_recuperacion)

        repetido = self.client.post("/api/admin/reclamos/retirar/", {"token": token}, format="json")
        self.assertEqual(repetido.status_code, 409)
        self.assertEqual(self.client.get(
            "/api/admin/reclamos/verificar-qr/", {"token": token}).data["estadoQr"], "USADO")
        self.assertEqual(self.client.get(
            f"/api/admin/reclamos/{self.reclamo.id}/qr-retiro/").status_code, 409)

        aviso = Notificacion.objects.filter(usuario=self.ana, tipo="RECLAMO").latest("id")
        self.assertIn("Retiro registrado", aviso.titulo)

    def test_qr_vencido_o_token_invalido_no_permite_retirar(self):
        self.aceptar(self.reclamo)
        token = self.reclamo.qr_retiro_token
        self.reclamo.qr_retiro_expira = timezone.now() - timedelta(minutes=1)
        self.reclamo.save(update_fields=["qr_retiro_expira"])
        self.como(self.biblioteca)

        verificado = self.client.get("/api/admin/reclamos/verificar-qr/", {"token": token})
        self.assertEqual(verificado.data["estadoQr"], "VENCIDO")
        self.assertFalse(verificado.data["valido"])
        self.assertEqual(self.client.get(
            f"/api/admin/reclamos/{self.reclamo.id}/qr-retiro/").status_code, 409)
        self.assertEqual(self.client.post(
            "/api/admin/reclamos/retirar/", {"token": token}, format="json").status_code, 409)
        self.assertEqual(self.client.post(
            "/api/admin/reclamos/retirar/", {"token": "no-existe"}, format="json").status_code, 404)
        self.assertEqual(self.client.get(
            "/api/admin/reclamos/verificar-qr/", {"token": ""}).status_code, 400)

        self.objeto.refresh_from_db()
        self.assertEqual(self.objeto.estado, "EN_CUSTODIA")

    def test_solo_biblioteca_o_admin_usa_la_bandeja(self):
        self.como(self.ana)
        self.assertEqual(self.client.get("/api/admin/reclamos/").status_code, 403)
        self.assertEqual(self.resolver(self.reclamo, "ACEPTADO").status_code, 403)
        self.assertEqual(self.client.get(
            f"/api/admin/reclamos/{self.reclamo.id}/qr-retiro/").status_code, 403)
        self.assertEqual(self.client.get(
            "/api/admin/reclamos/verificar-qr/", {"token": "x"}).status_code, 403)
        self.assertEqual(self.client.post(
            "/api/admin/reclamos/retirar/", {"token": "x"}, format="json").status_code, 403)
        self.reclamo.refresh_from_db()
        self.assertEqual(self.reclamo.estado, "PENDIENTE")

        self.como(self.admin)
        self.assertEqual(self.client.get("/api/admin/reclamos/").status_code, 200)
        self.assertEqual(self.resolver(self.reclamo, "ACEPTADO").status_code, 200)
