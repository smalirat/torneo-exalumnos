package com.mjt.Football_Tournament_Management_System;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExcelImportApiTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void previewChangesNothingAndApplyLoadsTheWorkbook() throws Exception {
        String admin = login();
        String s = UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        int year = 2801;
        byte[] file = workbook(year, s, "2", "1", null);

        JsonNode preview = upload(file, admin);
        assertEquals("PREVIEW", preview.get("status").asText());
        assertTrue(count(preview, "Partidos", "created") >= 1);
        assertTrue(seasonsWithYear(year).isEmpty(), "a preview must not save anything");

        JsonNode applied = read(post("/api/admin/imports/" + preview.get("importId").asLong() + "/apply", "{}", admin)
                .andExpect(status().isOk()));
        assertEquals("APPLIED", applied.get("status").asText());
        post("/api/admin/imports/" + preview.get("importId").asLong() + "/apply", "{}", admin)
                .andExpect(status().isConflict()); // already applied

        var tournament = seasonsWithYear(year).get(0).get("tournaments").get(0);
        assertEquals("APERTURA", tournament.get("stage").asText());
        var category = read(mvc.perform(get("/api/public/tournaments/" + tournament.get("id").asLong() + "/categories"))).get(0);
        long zone = category.get("zones").get(0).get("id").asLong();
        mvc.perform(get("/api/public/zones/" + zone + "/standings"))
                .andExpect(jsonPath("$[0].team.name").value("ALFA " + s))
                .andExpect(jsonPath("$[0].points").value(4)) // 3 win + 1 presence
                .andExpect(jsonPath("$[1].presencePoints").value(1));
        mvc.perform(get("/api/public/zones/" + zone + "/fixtures"))
                .andExpect(jsonPath("$[0].venue").value("Siberia"))
                .andExpect(jsonPath("$[0].pitch").value("3"))
                .andExpect(jsonPath("$[0].round").value(1))
                .andExpect(jsonPath("$[0].scheduledAt").value(year + "-04-05T09:00:00"));
        long seasonId = seasonsWithYear(year).get(0).get("id").asLong();
        mvc.perform(get("/api/public/seasons/" + seasonId + "/scorers"))
                .andExpect(jsonPath("$[0].player.fullName").value("Perez, Juan " + s))
                .andExpect(jsonPath("$[0].value").value(7));

        // Same file again: nothing to change
        JsonNode again = upload(file, admin);
        assertEquals(0, count(again, "Partidos", "created") + count(again, "Partidos", "updated"));
    }

    @Test
    void webEditsAreConflictsUntilTheAdminChoosesToOverwrite() throws Exception {
        String admin = login();
        String s = UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        int year = 2802;
        apply(upload(workbook(year, s, "2", "1", null), admin), "{}", admin);

        long tournament = seasonsWithYear(year).get(0).get("tournaments").get(0).get("id").asLong();
        long zone = read(mvc.perform(get("/api/public/tournaments/" + tournament + "/categories"))).get(0)
                .get("zones").get(0).get("id").asLong();
        long match = read(mvc.perform(get("/api/public/zones/" + zone + "/fixtures"))).get(0).get("id").asLong();
        // The admin corrects the result on the web: 2-2
        patch("/api/admin/matches/" + match + "/score", "{\"homeGoals\":2,\"awayGoals\":2}", admin).andExpect(status().isOk());

        // The Excel still says 2-1 and now has a note: conflict, skipped by default
        JsonNode preview = upload(workbook(year, s, "2", "1", "Se jugó con lluvia"), admin);
        assertEquals(1, preview.get("conflicts").size());
        var conflict = preview.get("conflicts").get(0);
        assertEquals("match:" + match, conflict.get("key").asText());
        assertEquals("2-1", conflict.get("excelValue").asText());
        assertEquals("2-2", conflict.get("webValue").asText());

        apply(preview, "{}", admin);
        mvc.perform(get("/api/public/zones/" + zone + "/fixtures")).andExpect(jsonPath("$[0].homeGoals").value(2))
                .andExpect(jsonPath("$[0].awayGoals").value(2));

        // New upload, this time overwriting that conflict
        JsonNode second = upload(workbook(year, s, "2", "1", "Se jugó con lluvia"), admin);
        apply(second, "{\"overwrite\":[\"match:" + match + "\"]}", admin);
        mvc.perform(get("/api/public/zones/" + zone + "/fixtures"))
                .andExpect(jsonPath("$[0].awayGoals").value(1))
                .andExpect(jsonPath("$[0].notes").value("Se jugó con lluvia"));

        mvc.perform(get("/api/admin/imports").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$[0].status").value("APPLIED"));
    }

    @Test
    void exportedWorkbookReimportsWithoutChangesIncludingWebData() throws Exception {
        String admin = login();
        String s = UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        int year = 2803;
        apply(upload(workbook(year, s, "2", "1", null), admin), "{}", admin);
        var season = seasonsWithYear(year).get(0);
        long seasonId = season.get("id").asLong();
        long tournament = season.get("tournaments").get(0).get("id").asLong();
        var category = read(mvc.perform(get("/api/public/tournaments/" + tournament + "/categories"))).get(0);
        long zone1 = category.get("zones").get(0).get("id").asLong();

        // Data created on the web: second zone with a team, an interzonal walkover, sanction, yellows, champion
        long zone2 = read(post("/api/admin/categories/" + category.get("id").asLong() + "/zones", "{\"name\":\"Zona 2\"}", admin)).get("id").asLong();
        long gamma = read(post("/api/teams/register", "{\"name\":\"GAMMA " + s + "\"}", admin)).get("id").asLong();
        long reg = read(post("/api/teams/" + gamma + "/tournaments/" + tournament + "/register", "", admin)).get("id").asLong();
        patch("/api/admin/registrations/" + reg + "/zone", "{\"zoneId\":" + zone2 + "}", admin).andExpect(status().isOk());
        var teams = read(mvc.perform(get("/api/public/zones/" + zone1 + "/standings")));
        long alfa = teams.get(0).get("team").get("id").asLong();
        long iz = read(post("/api/admin/fixtures/manual", "{\"tournamentId\":" + tournament + ",\"fixtures\":[{\"homeTeamId\":"
                + alfa + ",\"awayTeamId\":" + gamma + ",\"round\":2,\"roundLabel\":\"Interzonal\","
                + "\"scheduledAt\":\"" + year + "-04-12T10:10:00\",\"venue\":\"Anexo\",\"pitch\":\"1\"}]}", admin)).get(0).get("id").asLong();
        patch("/api/admin/matches/" + iz + "/score", "{\"awayPresent\":false}", admin).andExpect(status().isOk());
        long player = read(post("/api/teams/" + gamma + "/players", "{\"players\":[{\"fullName\":\"Gomez, Pedro " + s + "\",\"jerseyNo\":5}]}", admin)).get(0).get("id").asLong();
        post("/api/admin/seasons/" + seasonId + "/sanctions", "{\"playerId\":" + player + ",\"tournamentId\":" + tournament
                + ",\"type\":\"DIRECT_RED\",\"matchesSuspended\":2,\"matchesServed\":1}", admin).andExpect(status().isOk());
        put("/api/admin/tournaments/" + tournament + "/yellow-cards", "{\"playerId\":" + player + ",\"yellowCards\":3}", admin);
        put("/api/admin/champions", "{\"year\":" + year + ",\"categoryName\":\"Q" + s.substring(0, 3) + "\",\"teamId\":" + gamma + "}", admin);

        byte[] exported = mvc.perform(get("/api/admin/imports/export/" + seasonId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        JsonNode reimport = upload(exported, admin);
        for (var c : reimport.get("counters")) {
            assertEquals(0, c.get("created").asInt() + c.get("updated").asInt() + c.get("conflicts").asInt(),
                    "re-import of the export changed " + c + " — changes: " + reimport.get("changes"));
        }
        assertEquals(0, reimport.get("warnings").size(), "warnings: " + reimport.get("warnings"));
    }

    @Test
    void rejectsFilesThatAreNotTheLeagueWorkbook() throws Exception {
        String admin = login();
        mvc.perform(multipart("/api/admin/imports").file(new MockMultipartFile("file", "x.xlsx",
                        "application/octet-stream", "not excel".getBytes())).header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/admin/imports").file(new MockMultipartFile("file", "x.csv",
                        "text/csv", "a,b".getBytes())).header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    /** Full import of the league's real workbook (local only, see RealWorkbookParserTest). */
    @Test
    void importsTheRealWorkbook() throws Exception {
        var path = Path.of("design", "TORNEO CLAUSURA 2026-7.xlsx");
        assumeTrue(Files.exists(path), "real workbook not present");
        String admin = login();
        JsonNode preview = upload(Files.readAllBytes(path), admin);
        System.out.println("counters: " + preview.get("counters"));
        System.out.println("warnings (" + preview.get("warnings").size() + "):");
        preview.get("warnings").forEach(w -> System.out.println("  " + w.asText()));
        JsonNode applied = apply(preview, "{}", admin);
        assertEquals("APPLIED", applied.get("status").asText());

        // Apertura zone tables must match the Excel (points from results; PR follows our rule)
        var season = seasonsWithYear(2026).get(0);
        for (var t : season.get("tournaments")) {
            for (var c : read(mvc.perform(get("/api/public/tournaments/" + t.get("id").asLong() + "/categories")))) {
                for (var z : c.get("zones")) {
                    var table = read(mvc.perform(get("/api/public/zones/" + z.get("id").asLong() + "/standings")));
                    System.out.println(t.get("name").asText() + " " + c.get("name").asText() + " " + z.get("name").asText()
                            + ": " + StreamSupport.stream(table.spliterator(), false)
                            .map(r -> r.get("team").get("name").asText() + "=" + r.get("points").asInt()).toList());
                }
            }
        }
        // Re-importing the same file changes nothing
        JsonNode again = upload(Files.readAllBytes(path), admin);
        for (var c : again.get("counters")) {
            assertEquals(0, c.get("created").asInt() + c.get("updated").asInt(), "re-import changed " + c);
        }

        // Exporting what was loaded and importing it back changes nothing either
        byte[] exported = mvc.perform(get("/api/admin/imports/export/" + season.get("id").asLong())
                .header("Authorization", "Bearer " + admin)).andReturn().getResponse().getContentAsByteArray();
        Files.write(Path.of("target", "export-2026.xlsx"), exported);
        JsonNode roundTrip = upload(exported, admin);
        for (var c : roundTrip.get("counters")) {
            assertEquals(0, c.get("created").asInt() + c.get("updated").asInt() + c.get("conflicts").asInt(),
                    "round trip changed " + c + " — " + roundTrip.get("changes"));
        }
    }

    // ------------------------------------------------------------------ synthetic workbook

    /** Minimal workbook with the league's layout: one zone, one played match, one scorer. */
    private byte[] workbook(int year, String s, String homeGoals, String awayGoals, String note) throws Exception {
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var table = wb.createSheet("Tabla Apertura ");
            set(table, 1, 0, "TORNEO DE EXALUMNOS - COLEGIO MARIANISTA - AÑO " + year);
            set(table, 3, 0, "TORNEO APERTURA");
            set(table, 6, 0, "ZONA 1");
            row(table, 8, "POSICIÓN", "EQUIPO", "PTOS", "PJ", "PG", "PE", "PP", "GF", "GE", "DIF", "PR");
            row(table, 9, 1, "ALFA " + s, 4, 1, 1, 0, 0, 2, 1, 1, 1);
            row(table, 10, 2, "BETA " + s + " ", 1, 1, 0, 0, 1, 1, 2, -1, 1); // trailing space as in the real file

            var fixture = wb.createSheet("Part Jug");
            set(fixture, 1, 0, "TORNEO DE EXALUMNOS - COLEGIO MARIANISTA - AÑO " + year);
            set(fixture, 3, 0, "TORNEO APERTURA");
            set(fixture, 5, 0, "FECHA Nº 1");
            set(fixture, 5, 6, "Domingo 5 de Abril");
            row(fixture, 7, "Campo", "Cancha", "Horario", "Equipo", null, "vs", "Equipo", null, "Categoría");
            row(fixture, 9, "Siberia", 3, "9:00 hs", "ALFA " + s, homeGoals, "vs", "beta " + s, awayGoals, "Zona 1",
                    note == null ? null : "Nota: " + note);
            row(fixture, 10, "Anexo", 1, "9:00 hs", "LIBRE");

            var scorers = wb.createSheet("Goleadores");
            set(scorers, 1, 0, "TORNEO DE EXALUMNOS - COLEGIO MARIANISTA - AÑO " + year);
            row(scorers, 7, null, "Jugador", "Equipo", "Goles");
            row(scorers, 8, null, "Perez, Juan " + s, "ALFA " + s, 7);
            row(scorers, 9, null, null, "control", 0);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void set(XSSFSheet sheet, int r, int c, Object v) {
        var row = sheet.getRow(r) == null ? sheet.createRow(r) : sheet.getRow(r);
        var cell = row.createCell(c);
        if (v instanceof Number n) cell.setCellValue(n.doubleValue());
        else cell.setCellValue(String.valueOf(v));
    }

    private static void row(XSSFSheet sheet, int r, Object... values) {
        for (int c = 0; c < values.length; c++) if (values[c] != null) set(sheet, r, c, values[c]);
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode upload(byte[] file, String token) throws Exception {
        return read(mvc.perform(multipart("/api/admin/imports")
                        .file(new MockMultipartFile("file", "torneo.xlsx", "application/octet-stream", file))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
    }

    private JsonNode apply(JsonNode preview, String body, String token) throws Exception {
        return read(post("/api/admin/imports/" + preview.get("importId").asLong() + "/apply", body, token)
                .andExpect(status().isOk()));
    }

    private static int count(JsonNode res, String entity, String field) {
        for (var c : res.get("counters")) if (c.get("entity").asText().equals(entity)) return c.get(field).asInt();
        return 0;
    }

    private java.util.List<JsonNode> seasonsWithYear(int year) throws Exception {
        var out = new java.util.ArrayList<JsonNode>();
        for (var s : read(mvc.perform(get("/api/public/seasons")))) if (s.get("year").asInt() == year) out.add(s);
        return out;
    }

    private String login() throws Exception {
        return read(post("/api/auth/login", "{\"username\":\"admin\",\"password\":\"admin123\"}", null)).get("token").asText();
    }

    private ResultActions post(String url, String body, String token) throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) req.header("Authorization", "Bearer " + token);
        return mvc.perform(req);
    }

    private ResultActions patch(String url, String body, String token) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url)
                .contentType(MediaType.APPLICATION_JSON).content(body).header("Authorization", "Bearer " + token));
    }

    private ResultActions put(String url, String body, String token) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .contentType(MediaType.APPLICATION_JSON).content(body).header("Authorization", "Bearer " + token));
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }
}
