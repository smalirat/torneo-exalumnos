package com.mjt.Football_Tournament_Management_System.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Every text that arrives in a JSON body loses its invisible characters before reaching the code:
 * - control characters (except tab and line breaks): nobody types them, and PostgreSQL rejects \u0000 with an error;
 * - bidirectional overrides (U+202A–202E, U+2066–2069): they make a name display different from what it is, e.g. a
 *   team name that reads "Boca" on screen but is stored reversed.
 * HTML is not stripped on purpose: the frontend always shows data as text (Vue escapes it, there is no v-html), so a
 * "<script>" in a team name is shown as those characters and never runs. Removing it here would only corrupt data.
 */
@Configuration
public class TextSanitizing {
    private static final Pattern INVISIBLE = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F\\u202A-\\u202E\\u2066-\\u2069]");

    public static String clean(String s) {
        return s == null ? null : INVISIBLE.matcher(s).replaceAll("");
    }

    /** Spring Boot adds every Jackson Module bean to the JSON mapper it uses for request bodies */
    @Bean
    public Module textSanitizingModule() {
        var module = new SimpleModule("text-sanitizing");
        module.addDeserializer(String.class, new StringDeserializer() {
            @Override
            public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return clean(super.deserialize(p, ctxt));
            }
        });
        return module;
    }
}
