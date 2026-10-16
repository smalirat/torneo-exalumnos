package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.CollagePhoto;
import com.mjt.Football_Tournament_Management_System.exception.BadRequestException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.CollagePhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Champions' collage: upload, free layout (move/resize/bring to front), featured photo, delete. */
@Service
@RequiredArgsConstructor
public class CollageService {
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png");
    private static final long MAX_BYTES = 5 * 1024 * 1024;
    /** New photos start in a loose 4-column grid, slightly overlapped; the admin moves them afterwards. */
    private static final int COLUMNS = 4;
    private static final double NEW_SIZE = 0.25;

    private final CollagePhotoRepository repo;

    public List<ResponseDtos.CollagePhotoRes> list() { return repo.layout(); }

    public CollagePhotoRepository.Image image(Long id) {
        return repo.image(id).orElseThrow(() -> NotFoundException.of("Foto", id));
    }

    @Transactional
    public ResponseDtos.CollagePhotoRes upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("Elegí una foto");
        if (!TYPES.contains(file.getContentType())) throw new BadRequestException("La foto tiene que ser JPG o PNG");
        if (file.getSize() > MAX_BYTES) throw new BadRequestException("La foto pesa más de 5 MB");
        byte[] data;
        BufferedImage img;
        try {
            data = file.getBytes();
            img = ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new BadRequestException("No se pudo leer la foto");
        }
        if (img == null) throw new BadRequestException("No se pudo leer la foto");

        int n = (int) repo.count();
        var p = new CollagePhoto();
        p.setContentType(file.getContentType());
        p.setImageData(data);
        p.setWidth(img.getWidth());
        p.setHeight(img.getHeight());
        p.setSize(NEW_SIZE);
        // Small deterministic offsets so the grid does not look ruled
        p.setPosX(0.005 + (n % COLUMNS) * 0.24 + (n * 7 % 3) / 200.0); // right edge stays inside the board
        p.setPosY((n / COLUMNS) * 0.19 + (n * 3 % 4) / 100.0);
        p.setLayer(repo.maxLayer() + 1);
        p.setCreatedAt(LocalDateTime.now());
        return ResponseDtos.CollagePhotoRes.from(repo.save(p));
    }

    /** Saves position, size and layer of the given photos (the ones the admin touched). */
    @Transactional
    public List<ResponseDtos.CollagePhotoRes> updateLayout(AdminDtos.CollageLayoutReq req) {
        for (var item : req.getItems()) {
            var p = repo.findById(item.getId()).orElseThrow(() -> NotFoundException.of("Foto", item.getId()));
            p.setPosX(item.getX());
            p.setPosY(item.getY());
            p.setSize(item.getW());
            p.setLayer(item.getZ());
        }
        repo.flush();
        return repo.layout();
    }

    /** Marks (or unmarks) the featured photo; marking one unmarks the previous. */
    @Transactional
    public List<ResponseDtos.CollagePhotoRes> setFeatured(Long id, boolean featured) {
        var p = repo.findById(id).orElseThrow(() -> NotFoundException.of("Foto", id));
        if (featured) repo.clearFeaturedExcept(id);
        p.setFeatured(featured);
        repo.flush();
        return repo.layout();
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw NotFoundException.of("Foto", id);
        repo.deleteById(id);
    }
}
