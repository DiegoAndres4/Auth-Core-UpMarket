package com.pamplona.authcore.infrastructure.adapters.out.email;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Contenido del correo de verificacion (espanol, texto plano + HTML con la
 * identidad visual de UPMarket).
 */
public final class VerificationEmailContent {

    public static final String SUBJECT = "Confirma tu correo en UPMarket";

    private static final String TEMPLATE_PATH = "/templates/correo-verificacion.html";
    private static final String LINK_PLACEHOLDER = "${LINK}";
    private static final String TEMPLATE = loadTemplate();

    private VerificationEmailContent() {
    }

    public static String plainText(String link) {
        return "Hola,\n\n"
                + "Gracias por registrarte en UPMarket. Confirma tu correo con el siguiente enlace; expira en 24 horas:\n"
                + link + "\n\n"
                + "Si no creaste esta cuenta, ignora este correo.\n";
    }

    public static String html(String link) {
        return TEMPLATE.replace(LINK_PLACEHOLDER, escapeHtml(link));
    }

    private static String loadTemplate() {
        try (InputStream in = VerificationEmailContent.class.getResourceAsStream(TEMPLATE_PATH)) {
            if (in == null) {
                throw new IllegalStateException(
                        "No se encontro la plantilla del correo: " + TEMPLATE_PATH);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "No se pudo leer la plantilla del correo: " + TEMPLATE_PATH, ex);
        }
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
