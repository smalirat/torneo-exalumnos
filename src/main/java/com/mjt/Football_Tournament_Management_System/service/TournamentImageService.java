package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import com.mjt.Football_Tournament_Management_System.entire.TournamentImage;
import com.mjt.Football_Tournament_Management_System.exception.BadRequestException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.TournamentImageRepository;
import com.mjt.Football_Tournament_Management_System.repo.TournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

/** Banner (cover) and logo of an edition: PNG or JPG up to 5 MB, set by the admin. */
@Service
@RequiredArgsConstructor
public class TournamentImageService {
    private static final long MAX_BYTES = 5 * 1024 * 1024;

    private final TournamentRepository tournamentRepo;
    private final TournamentImageRepository imageRepo;

    @Transactional
    public ResponseDtos.TournamentRes set(Long tournamentId, String kindName, MultipartFile file) {
        var t = findTournament(tournamentId);
        var kind = kind(kindName);
        var img = SafeImages.read(file, MAX_BYTES, "La imagen");
        var image = imageRepo.findByTournamentIdAndKind(t.getId(), kind).orElseGet(TournamentImage::new);
        image.setTournamentId(t.getId());
        image.setKind(kind);
        image.setContentType(img.contentType());
        image.setImageData(img.data());
        imageRepo.save(image);
        // Seconds since epoch: a new value on every upload, so cached URLs never go stale
        setVersion(t, kind, (int) (System.currentTimeMillis() / 1000));
        return ResponseDtos.TournamentRes.from(t);
    }

    @Transactional
    public ResponseDtos.TournamentRes remove(Long tournamentId, String kindName) {
        var t = findTournament(tournamentId);
        var kind = kind(kindName);
        imageRepo.findByTournamentIdAndKind(t.getId(), kind).ifPresent(imageRepo::delete);
        setVersion(t, kind, null);
        return ResponseDtos.TournamentRes.from(t);
    }

    @Transactional(readOnly = true)
    public TournamentImage get(Long tournamentId, String kindName) {
        return imageRepo.findByTournamentIdAndKind(tournamentId, kind(kindName))
                .orElseThrow(() -> new NotFoundException("La edición no tiene esa imagen"));
    }

    private static void setVersion(Tournament t, TournamentImage.Kind kind, Integer version) {
        if (kind == TournamentImage.Kind.BANNER) t.setBannerVersion(version);
        else t.setLogoVersion(version);
    }

    /** "banner" or "logo" (from the URL) */
    private static TournamentImage.Kind kind(String name) {
        try {
            return TournamentImage.Kind.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Imagen desconocida: " + name);
        }
    }

    private Tournament findTournament(Long id) {
        return tournamentRepo.findById(id).orElseThrow(() -> NotFoundException.of("Campeonato", id));
    }
}
