package com.pamplona.authcore.infrastructure.adapters.out.email;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica el mensaje multipart que arma el adaptador SMTP. */
class SmtpEmailSenderAdapterTest {

    private static final String LINK = "http://localhost:5173/#/verificar?token=abc123";
    private static final String RECIPIENT = "ana@example.com";

    @Test
    void elMensajeEnviadoTienePartePlanaYParteHtmlYLaMarca() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        new SmtpEmailSenderAdapter(mailSender, "no-responder@upmarket.test")
                .sendVerificationEmail(RECIPIENT, LINK);

        verify(mailSender).send(message);
        assertEquals(VerificationEmailContent.SUBJECT, message.getSubject());
        assertEquals(RECIPIENT, message.getAllRecipients()[0].toString());

        ByteArrayOutputStream serializado = new ByteArrayOutputStream();
        message.writeTo(serializado);
        MimeMessage enviado = new MimeMessage(
                Session.getInstance(new Properties()),
                new ByteArrayInputStream(serializado.toByteArray()));

        Object content = enviado.getContent();
        assertTrue(content instanceof MimeMultipart, "El correo debe ser multipart");
        List<Hoja> hojas = hojas((MimeMultipart) content);
        assertEquals(2, hojas.size());

        buscar(hojas, "text/plain");
        Hoja parteHtml = buscar(hojas, "text/html");

        String html = parteHtml.parte().getContent().toString();
        assertTrue(html.contains(">UP<"));
        assertTrue(html.contains(">Market<"));
        assertTrue(html.contains("Confirmar mi correo"));
        assertTrue(html.contains(
                "El enlace vence en 24 horas. Si no creaste esta cuenta, ignora este correo."));
        assertEquals(2, contar(html, LINK));
    }

    private record Hoja(String tipo, Part parte) {
    }

    private static List<Hoja> hojas(MimeMultipart multipart) throws Exception {
        List<Hoja> hojas = new ArrayList<>();
        for (int i = 0; i < multipart.getCount(); i++) {
            Part parte = multipart.getBodyPart(i);
            String tipo = parte.getContentType();
            Object contenido = parte.getContent();
            if (contenido instanceof Multipart anidado) {
                hojas.addAll(hojas((MimeMultipart) anidado));
            } else {
                hojas.add(new Hoja(tipo, parte));
            }
        }
        return hojas;
    }

    private static Hoja buscar(List<Hoja> hojas, String tipo) {
        for (Hoja hoja : hojas) {
            if (hoja.tipo().startsWith(tipo)) {
                return hoja;
            }
        }
        throw new AssertionError("No se encontro la parte " + tipo + " en " + hojas.stream().map(Hoja::tipo).toList());
    }

    private static int contar(String texto, String fragmento) {
        int total = 0;
        int desde = texto.indexOf(fragmento);
        while (desde >= 0) {
            total++;
            desde = texto.indexOf(fragmento, desde + fragmento.length());
        }
        return total;
    }
}
