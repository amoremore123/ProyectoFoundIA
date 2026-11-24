"""H34: eliminación real de la cuenta (Ley 29733): anonimizar, borrar y conservar lo que corresponde."""
from datetime import date, datetime, timedelta, timezone as dt_timezone
from unittest import mock

import bcrypt
from django.core import mail
from django.test import TestCase
from rest_framework.test import APITestCase

from . import eliminacion
from .models import (
    ArchivoPorBorrar, AvisoCoincidencia, BloqueoChat, Categoria, Coincidencia, Contacto, Conversacion, Dispositivo,
    Foto, Invitacion, LecturaChat, MensajeChat, Notificacion, Objeto, PreferenciaNotificacion, Reclamo,
    RecordatorioObjeto, Reporte, ReporteMensaje, SolicitudEliminacion, TokenRecuperacionPassword, TokenRevocado,
    Usuario,
)
from .tests import PASSWORD, token_de

RUTA = "/api/admin/solicitudes-eliminacion/"


class Escenario:
    """Una persona (Rosa) con datos en todas las tablas que toca H34, y otra (Luis) que conversa con ella."""

    def crear_escenario(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        crear = lambda nombre, correo, rol="USUARIO": Usuario.objects.create(  # noqa: E731
            nombre=nombre, apellido="Prueba", correo=correo, password=hash_, rol=rol, estado="ACTIVO")
        self.admin = crear("Ada", "admin-h34@prueba.invalid", "ADMIN")
        self.otro_admin = crear("Otto", "admin2-h34@prueba.invalid", "ADMIN")
        self.biblio = crear("Beto", "biblio-h34@prueba.invalid", "BIBLIOTECA")
        self.rosa = crear("Rosa", "rosa-h34@prueba.invalid")
        self.luis = crear("Luis", "luis-h34@prueba.invalid")
        Usuario.objects.filter(pk=self.rosa.pk).update(foto_perfil_url="perfiles/aaaaaaaa-0000-4000-8000-000000000001.jpg",
                                                       sesion_version=3)
        self.rosa.refresh_from_db()
        cat = Categoria.objects.create(nombre="Mochilas H34")
        objeto = lambda dueno, estado, tipo="PERDIDO": Objeto.objects.create(  # noqa: E731
            usuario=dueno, categoria=cat, nombre=f"Objeto {estado}", descripcion="Mi mochila con mi DNI 12345678",
            ubicacion="Pabellón 4", latitud=-12.04, longitud=-77.03, fecha_objeto=date(2026, 11, 20), tipo=tipo,
            estado=estado)
        self.activo = objeto(self.rosa, "ACTIVO")
        self.oculto = objeto(self.rosa, "OCULTO")
        self.custodia = objeto(self.rosa, "EN_CUSTODIA", "ENCONTRADO")
        self.recuperado = objeto(self.rosa, "RECUPERADO")
        self.de_luis = objeto(self.luis, "ACTIVO", "ENCONTRADO")
        self.otro_de_luis = objeto(self.luis, "EN_CUSTODIA", "ENCONTRADO")
        self.foto = Foto.objects.create(objeto=self.activo, url="/uploads/bbbbbbbb-0000-4000-8000-000000000002.jpg",
                                        nombre_archivo="x.jpg")
        self.foto_custodia = Foto.objects.create(objeto=self.custodia, url="/uploads/cccccccc-0000-4000-8000-000000000003.jpg",
                                                 nombre_archivo="y.jpg")
        self.conversacion = Conversacion.objects.create(tipo="ENTRE_USUARIOS", objeto_id=self.de_luis.id,
                                                        usuario_a=self.rosa, usuario_b=self.luis)
        self.msg_rosa = MensajeChat.objects.create(conversacion_id=self.conversacion.id, autor=self.rosa,
                                                   texto="Mi celular es 987654321", imagen_url="chat/dddddddd-0000-4000-8000-000000000004.png")
        self.msg_luis = MensajeChat.objects.create(conversacion_id=self.conversacion.id, autor=self.luis, texto="Hola Rosa")
        LecturaChat.objects.create(conversacion_id=self.conversacion.id, usuario=self.rosa)
        BloqueoChat.objects.create(bloqueador=self.luis, bloqueado=self.rosa)
        self.aviso = Notificacion.objects.create(usuario=self.rosa, titulo="Coincidencia", mensaje="Hay una coincidencia")
        self.aviso_luis = Notificacion.objects.create(usuario=self.luis, titulo="Mensaje", mensaje="Mensaje nuevo")
        AvisoCoincidencia.objects.create(objeto_perdido_id=self.activo.id, objeto_encontrado_id=self.de_luis.id,
                                         notificacion_id=self.aviso.id)
        RecordatorioObjeto.objects.create(objeto_id=self.activo.id, periodo="2026-11", notificacion_id=self.aviso.id)
        Dispositivo.objects.create(usuario=self.rosa, token="fcm-rosa", plataforma="ANDROID")
        TokenRevocado.objects.create(jti="jti-rosa", usuario=self.rosa, expira_en=datetime(2030, 1, 1, tzinfo=dt_timezone.utc))
        PreferenciaNotificacion.objects.create(usuario=self.rosa)
        TokenRecuperacionPassword.objects.create(usuario=self.rosa, token_hash="h" * 64)
        Invitacion.objects.create(codigo_hash="i" * 64, usuario=self.rosa)
        self.reclamo_abierto = Reclamo.objects.create(objeto=self.de_luis, usuario=self.rosa, estado="PENDIENTE",
                                                      respuesta_verificacion="Tiene mi nombre bordado", qr_retiro_token="qr")
        self.reclamo_cerrado = Reclamo.objects.create(objeto=self.otro_de_luis, usuario=self.rosa, estado="RECHAZADO",
                                                      respuesta_verificacion="Otra prueba")
        coincidencia = Coincidencia.objects.create(objeto_perdido=self.activo, objeto_encontrado=self.de_luis,
                                                   nivel="ALTA", estado="PENDIENTE")
        self.contacto = Contacto.objects.create(coincidencia=coincidencia, usuario_emisor=self.rosa,
                                                usuario_receptor=self.luis, mensaje="Escríbeme a rosa@gmail.com")
        self.reporte = Reporte.objects.create(usuario=self.rosa, objeto=self.de_luis, motivo="Spam",
                                              descripcion="Me llamo Rosa y vi esto")
        self.reporte_msg = ReporteMensaje.objects.create(mensaje=self.msg_luis, usuario=self.rosa, motivo="Me escribió feo")
        self.solicitud = SolicitudEliminacion.objects.create(usuario=self.rosa)


class EliminacionRealTests(Escenario, APITestCase):
    def setUp(self):
        self.crear_escenario()

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def atender(self, confirmacion="rosa-h34@prueba.invalid", solicitud=None, quien=None):
        self.como(quien or self.admin)
        return self.client.post(f"{RUTA}{(solicitud or self.solicitud).id}/atender/",
                                {"confirmacion": confirmacion}, format="json")

    def test_lista_y_vista_previa(self):
        self.como(self.admin)
        lista = self.client.get(RUTA).data["results"]
        self.assertEqual([s["id"] for s in lista], [self.solicitud.id])
        self.assertEqual(lista[0]["usuario"]["correo"], "rosa-h34@prueba.invalid")
        self.assertFalse(lista[0]["requiereReaplicar"])
        detalle = self.client.get(f"{RUTA}{self.solicitud.id}/").data
        self.assertEqual(detalle["efecto"]["publicacionesQueSeEliminan"], 2)
        self.assertEqual(detalle["efecto"]["publicacionesQueSeConservan"], 2)
        self.assertEqual(detalle["efecto"]["fotosDePublicaciones"], 1)
        self.assertTrue(detalle["efecto"]["fotoDePerfil"])
        self.assertEqual(detalle["efecto"]["fotosDelChat"], 1)
        self.assertEqual(detalle["efecto"]["reclamosQueSeRetiran"], 1)
        # La vista previa no cambia nada
        self.rosa.refresh_from_db()
        self.assertEqual(self.rosa.estado, "ACTIVO")

    def test_atender_anonimiza_la_cuenta(self):
        r = self.atender()
        self.assertEqual(r.status_code, 200, r.data)
        self.assertEqual(r.data["estado"], "ATENDIDA")
        self.assertEqual(r.data["atendidaPor"]["id"], self.admin.id)
        self.rosa.refresh_from_db()
        self.assertEqual((self.rosa.nombre, self.rosa.apellido), ("Cuenta eliminada", ""))
        self.assertEqual(self.rosa.correo, f"eliminada-{self.rosa.id}@foundia.invalid")
        self.assertEqual(self.rosa.estado, "ELIMINADO")
        self.assertIsNone(self.rosa.foto_perfil_url)
        self.assertEqual(self.rosa.sesion_version, 4)  # se cierran todas las sesiones
        self.assertFalse(bcrypt.checkpw(PASSWORD.encode(), self.rosa.password.encode()))
        # El correo original queda libre para registrarse de nuevo
        self.assertFalse(Usuario.objects.filter(correo="rosa-h34@prueba.invalid").exists())

    def test_atender_borra_lo_que_solo_sirve_a_la_persona(self):
        self.atender()
        for modelo in (Dispositivo, TokenRevocado, PreferenciaNotificacion, TokenRecuperacionPassword, Invitacion,
                       LecturaChat):
            with self.subTest(tabla=modelo.__name__):
                self.assertFalse(modelo.objects.filter(usuario=self.rosa).exists())
        self.assertFalse(Notificacion.objects.filter(usuario=self.rosa).exists())
        self.assertTrue(Notificacion.objects.filter(pk=self.aviso_luis.pk).exists())  # lo de Luis no se toca
        self.assertFalse(BloqueoChat.objects.filter(bloqueado=self.rosa).exists())
        # Las referencias a sus avisos se sueltan sin borrar el registro
        self.assertIsNone(AvisoCoincidencia.objects.get().notificacion_id)
        self.assertIsNone(RecordatorioObjeto.objects.get().notificacion_id)

    def test_publicaciones_se_eliminan_o_se_conservan_sin_ubicacion(self):
        self.atender()
        for objeto in (self.activo, self.oculto):
            objeto.refresh_from_db()
            self.assertEqual(objeto.estado, "ELIMINADO")
            self.assertEqual(objeto.descripcion, "[eliminada a pedido de la persona]")
            self.assertEqual((objeto.ubicacion, objeto.latitud, objeto.longitud), (None, None, None))
        self.assertFalse(Foto.objects.filter(pk=self.foto.pk).exists())
        for objeto, estado in ((self.custodia, "EN_CUSTODIA"), (self.recuperado, "RECUPERADO")):
            objeto.refresh_from_db()
            self.assertEqual(objeto.estado, estado)          # registro de la biblioteca
            self.assertIsNone(objeto.ubicacion)
            self.assertEqual(objeto.usuario_id, self.rosa.id)
        self.assertTrue(Foto.objects.filter(pk=self.foto_custodia.pk).exists())
        self.de_luis.refresh_from_db()
        self.assertEqual(self.de_luis.estado, "ACTIVO")

    def test_chat_reclamos_contactos_y_reportes(self):
        self.atender()
        self.msg_rosa.refresh_from_db()
        self.assertEqual((self.msg_rosa.texto, self.msg_rosa.imagen_url), ("Mensaje eliminado", None))
        self.msg_luis.refresh_from_db()
        self.assertEqual(self.msg_luis.texto, "Hola Rosa")
        self.conversacion.refresh_from_db()
        self.assertEqual(self.conversacion.estado, "SOLO_LECTURA")
        self.reclamo_abierto.refresh_from_db()
        self.assertEqual((self.reclamo_abierto.estado, self.reclamo_abierto.respuesta_verificacion,
                          self.reclamo_abierto.qr_retiro_token), ("RETIRADO", None, None))
        self.reclamo_cerrado.refresh_from_db()
        self.assertEqual((self.reclamo_cerrado.estado, self.reclamo_cerrado.respuesta_verificacion), ("RECHAZADO", None))
        self.contacto.refresh_from_db()
        self.assertEqual(self.contacto.mensaje, "[eliminado]")
        self.reporte.refresh_from_db()
        self.assertEqual(self.reporte.descripcion, "[eliminado]")
        self.reporte_msg.refresh_from_db()
        self.assertEqual(self.reporte_msg.motivo, "[eliminado]")

    def test_fotos_anotadas_para_spring(self):
        r = self.atender()
        rutas = set(ArchivoPorBorrar.objects.values_list("ruta", flat=True))
        self.assertEqual(rutas, {"perfiles/aaaaaaaa-0000-4000-8000-000000000001.jpg",
                                 "/uploads/bbbbbbbb-0000-4000-8000-000000000002.jpg",
                                 "chat/dddddddd-0000-4000-8000-000000000004.png"})
        self.assertEqual(r.data["resumen"]["archivosPorBorrar"], 3)

    def test_correo_de_confirmacion_a_la_direccion_original(self):
        r = self.atender()
        self.assertTrue(r.data["correoEnviado"])
        self.assertEqual(len(mail.outbox), 1)
        self.assertEqual(mail.outbox[0].to, ["rosa-h34@prueba.invalid"])
        self.assertIn("eliminada", mail.outbox[0].subject)

    def test_si_el_correo_falla_la_eliminacion_queda_hecha(self):
        with mock.patch.object(eliminacion, "send_mail", side_effect=OSError("sin servidor")):
            r = self.atender()
        self.assertEqual(r.status_code, 200)
        self.assertFalse(r.data["correoEnviado"])
        self.rosa.refresh_from_db()
        self.assertEqual(self.rosa.estado, "ELIMINADO")

    def test_confirmacion_incorrecta_no_hace_nada(self):
        for confirmacion in ("otra@prueba.invalid", ""):
            with self.subTest(confirmacion=confirmacion):
                r = self.atender(confirmacion=confirmacion)
                self.assertEqual(r.status_code, 400)
                self.assertIn("confirmacion", r.data)
        self.rosa.refresh_from_db()
        self.assertEqual((self.rosa.estado, self.rosa.correo), ("ACTIVO", "rosa-h34@prueba.invalid"))
        self.assertTrue(Dispositivo.objects.filter(usuario=self.rosa).exists())
        self.assertEqual(ArchivoPorBorrar.objects.count(), 0)
        # Mayúsculas y espacios no importan
        self.assertEqual(self.atender(confirmacion="  ROSA-h34@prueba.invalid ").status_code, 200)

    def test_si_algo_falla_no_cambia_nada(self):
        # (y no se envía el correo de confirmación)
        with mock.patch.object(eliminacion.Contacto.objects, "filter", side_effect=RuntimeError("falla a mitad")):
            with self.assertRaises(RuntimeError):
                self.atender()
        self.rosa.refresh_from_db()
        self.assertEqual(self.rosa.estado, "ACTIVO")
        self.assertTrue(Foto.objects.filter(pk=self.foto.pk).exists())
        self.assertTrue(Notificacion.objects.filter(usuario=self.rosa).exists())
        self.solicitud.refresh_from_db()
        self.assertEqual(self.solicitud.estado, "PENDIENTE")
        self.assertEqual(len(mail.outbox), 0)

    def test_reglas_409(self):
        self.atender()
        self.assertEqual(self.atender().status_code, 409)  # ya atendida
        solicitud_biblio = SolicitudEliminacion.objects.create(usuario=self.biblio)
        self.assertEqual(self.atender("biblio-h34@prueba.invalid", solicitud_biblio).status_code, 409)
        self.biblio.refresh_from_db()
        self.assertEqual(self.biblio.estado, "ACTIVO")
        solicitud_admin = SolicitudEliminacion.objects.create(usuario=self.admin)
        self.assertEqual(self.atender("admin-h34@prueba.invalid", solicitud_admin).status_code, 409)

    def test_rechazar_con_motivo(self):
        self.como(self.admin)
        self.assertEqual(self.client.post(f"{RUTA}{self.solicitud.id}/rechazar/", {"motivo": "x"}, format="json").status_code, 400)
        r = self.client.post(f"{RUTA}{self.solicitud.id}/rechazar/", {"motivo": "La solicitud no la hizo la titular."},
                             format="json")
        self.assertEqual(r.status_code, 200)
        self.assertEqual((r.data["estado"], r.data["motivoRechazo"]), ("RECHAZADA", "La solicitud no la hizo la titular."))
        self.rosa.refresh_from_db()
        self.assertEqual(self.rosa.estado, "ACTIVO")
        self.assertEqual(self.atender().status_code, 409)

    def test_filtro_plazo_y_vencidas(self):
        self.como(self.admin)
        self.assertEqual(self.client.get(f"{RUTA}?estado=PENDIENTE").data["count"], 1)
        self.assertEqual(self.client.get(f"{RUTA}?estado=ATENDIDA").data["count"], 0)
        self.assertEqual(self.client.get(f"{RUTA}?estado=OTRO").status_code, 400)
        # Pedida el lunes 2/11/2026 en Lima: 10 días hábiles → lunes 16/11/2026
        lunes = datetime(2026, 11, 2, 15, 0, tzinfo=dt_timezone.utc)
        self.assertEqual(str(eliminacion.fecha_limite(lunes)), "2026-11-16")
        SolicitudEliminacion.objects.filter(pk=self.solicitud.pk).update(fecha_solicitud=lunes)
        fila = self.client.get(RUTA).data["results"][0]
        self.assertEqual(fila["fechaLimite"], "2026-11-16")
        self.assertTrue(fila["vencida"])

    def test_permisos(self):
        self.assertEqual(self.client.get(RUTA).status_code, 401)
        for quien in (self.biblio, self.luis):
            self.como(quien)
            self.assertEqual(self.client.get(RUTA).status_code, 403)
            self.assertEqual(self.client.post(f"{RUTA}{self.solicitud.id}/atender/",
                                              {"confirmacion": "rosa-h34@prueba.invalid"}, format="json").status_code, 403)
        self.rosa.refresh_from_db()
        self.assertEqual(self.rosa.estado, "ACTIVO")
        self.como(self.admin)
        self.assertEqual(self.client.get(f"{RUTA}999999/").status_code, 404)


class ReaplicarTests(Escenario, TestCase):
    """H70: si una restauración trae de vuelta la cuenta, se vuelve a anonimizar."""

    def setUp(self):
        self.crear_escenario()

    def test_reaplicar_tras_una_restauracion(self):
        eliminacion.anonimizar(self.rosa)
        SolicitudEliminacion.objects.filter(pk=self.solicitud.pk).update(estado="ATENDIDA")
        self.assertEqual(eliminacion.reaplicar_eliminaciones(), 0)  # ya estaba anonimizada
        # Una restauración devuelve los datos viejos
        Usuario.objects.filter(pk=self.rosa.pk).update(nombre="Rosa", correo="rosa-h34@prueba.invalid", estado="ACTIVO")
        Dispositivo.objects.create(usuario=self.rosa, token="fcm-rosa-2", plataforma="ANDROID")
        self.assertEqual(eliminacion.reaplicar_eliminaciones(), 1)
        self.rosa.refresh_from_db()
        self.assertEqual((self.rosa.estado, self.rosa.nombre), ("ELIMINADO", "Cuenta eliminada"))
        self.assertFalse(Dispositivo.objects.filter(usuario=self.rosa).exists())
        self.assertEqual(eliminacion.reaplicar_eliminaciones(), 0)
