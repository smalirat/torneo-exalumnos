package com.mjt.Football_Tournament_Management_System;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CollageApiTests extends ApiTestSupport {

    @Test
    void adminUploadsMovesFeaturesAndDeletesPhotos() throws Exception {
        String admin = admin();
        byte[] png = png(40, 20);
        var first = read(upload(png, "image/png", admin).andExpect(status().isOk()));
        long a = first.get("id").asLong();
        long b = read(upload(png(10, 10), "image/png", admin).andExpect(status().isOk())).get("id").asLong();
        org.junit.jupiter.api.Assertions.assertEquals(40, first.get("width").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(20, first.get("height").asInt());

        // Public: layout and the image itself, cached
        var image = get("/api/public/collage/" + a + "/image", null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(png, image);

        put("/api/admin/collage/layout", "{\"items\":[{\"id\":" + a + ",\"x\":0.5,\"y\":0.3,\"w\":0.4,\"z\":99}]}", admin)
                .andExpect(status().isOk());
        get("/api/public/collage", null)
                .andExpect(jsonPath("$[?(@.id == " + a + ")].x").value(0.5))
                .andExpect(jsonPath("$[?(@.id == " + a + ")].w").value(0.4))
                .andExpect(jsonPath("$[?(@.id == " + a + ")].z").value(99))
                .andExpect(jsonPath("$[?(@.id == " + a + ")].url").value("/api/public/collage/" + a + "/image"));

        // Only one featured photo
        put("/api/admin/collage/" + a + "/featured", "{\"featured\":true}", admin).andExpect(status().isOk());
        put("/api/admin/collage/" + b + "/featured", "{\"featured\":true}", admin).andExpect(status().isOk());
        get("/api/public/collage", null)
                .andExpect(jsonPath("$[?(@.id == " + a + ")].featured").value(false))
                .andExpect(jsonPath("$[?(@.id == " + b + ")].featured").value(true));

        delete("/api/admin/collage/" + a, admin).andExpect(status().isNoContent());
        get("/api/public/collage/" + a + "/image", null).andExpect(status().isNotFound());
        delete("/api/admin/collage/" + b, admin).andExpect(status().isNoContent());
    }

    @Test
    void rejectsNonImagesBadLayoutsAndNonAdmins() throws Exception {
        String admin = admin();
        upload("hola".getBytes(), "text/plain", admin).andExpect(status().isBadRequest());
        upload("no es un png".getBytes(), "image/png", admin).andExpect(status().isBadRequest());
        upload(png(5, 5), "image/png", newUser()).andExpect(status().isForbidden());
        upload(png(5, 5), "image/png", null).andExpect(status().isUnauthorized());

        long id = read(upload(png(5, 5), "image/png", admin)).get("id").asLong();
        put("/api/admin/collage/layout", "{\"items\":[{\"id\":" + id + ",\"x\":0.5,\"y\":0.3,\"w\":0.01,\"z\":1}]}", admin)
                .andExpect(status().isBadRequest()); // too small to see
        put("/api/admin/collage/layout", "{\"items\":[]}", admin).andExpect(status().isBadRequest());
        delete("/api/admin/collage/" + id, admin).andExpect(status().isNoContent());
    }

    private ResultActions upload(byte[] data, String type, String token) throws Exception {
        var req = multipart("/api/admin/collage").file(new MockMultipartFile("file", "foto", type, data));
        if (token != null) req.header("Authorization", "Bearer " + token);
        return mvc.perform(req);
    }

    private static byte[] png(int w, int h) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
