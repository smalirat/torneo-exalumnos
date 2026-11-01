package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.exception.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Checks an uploaded PNG/JPG and re-encodes it. What is stored and served is always a freshly drawn image, never the
 * uploaded bytes:
 * - the real format comes from the file's content, not from the Content-Type the browser sent;
 * - the size in pixels is read from the header before decoding: a small file that declares 50000x50000 pixels would
 *   otherwise take gigabytes of memory to decode ("decompression bomb");
 * - re-encoding drops the metadata (a phone photo's EXIF can carry the GPS position where it was taken) and anything
 *   hidden after the image data (a file that is both a valid image and an HTML or script).
 */
public final class SafeImages {
    /** Plenty for a crest, banner or photo; decoding 40 MP takes about 160 MB */
    static final long MAX_PIXELS = 40_000_000;
    static final int MAX_SIDE = 10_000;

    public record Image(String contentType, byte[] data, int width, int height) {}

    private SafeImages() {}

    /** @param what how the error messages name the file ("La foto", "El escudo", "La imagen") */
    public static Image read(MultipartFile file, long maxBytes, String what) {
        if (file == null || file.isEmpty()) throw new BadRequestException("Elegí una imagen");
        if (file.getSize() > maxBytes) {
            throw new BadRequestException(what + " pesa más de " + maxBytes / (1024 * 1024) + " MB");
        }
        try {
            return sanitize(file.getBytes(), what);
        } catch (IOException e) {
            throw new BadRequestException("No se pudo leer " + what.toLowerCase());
        }
    }

    static Image sanitize(byte[] bytes, String what) throws IOException {
        try (var in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) throw notAnImage(what);
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase();
                boolean png = format.equals("png");
                if (!png && !format.equals("jpeg")) throw notAnImage(what);
                reader.setInput(in, true, true); // ignore metadata: it is never read, and dropped on re-encode
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE
                        || (long) width * height > MAX_PIXELS) {
                    throw new BadRequestException(what + " es demasiado grande (máximo " + MAX_SIDE + " px por lado)");
                }
                BufferedImage img = reader.read(0);
                return png ? new Image("image/png", writePng(img), width, height)
                        : new Image("image/jpeg", writeJpeg(img), width, height);
            } finally {
                reader.dispose();
            }
        }
    }

    private static byte[] writePng(BufferedImage img) throws IOException {
        var out = new ByteArrayOutputStream();
        if (!ImageIO.write(img, "png", out)) throw new IOException("no PNG writer");
        return out.toByteArray();
    }

    /** JPEG has no transparency: the image is redrawn as plain RGB, at high quality */
    private static byte[] writeJpeg(BufferedImage img) throws IOException {
        var rgb = img;
        if (img.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            var g = rgb.createGraphics();
            g.drawImage(img, 0, 0, java.awt.Color.WHITE, null);
            g.dispose();
        }
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        var out = new ByteArrayOutputStream();
        try (var ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            var param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.9f);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static BadRequestException notAnImage(String what) {
        return new BadRequestException(what + " tiene que ser PNG o JPG");
    }
}
