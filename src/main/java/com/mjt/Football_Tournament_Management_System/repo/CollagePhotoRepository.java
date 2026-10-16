package com.mjt.Football_Tournament_Management_System.repo;

import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.CollagePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CollagePhotoRepository extends JpaRepository<CollagePhoto, Long> {

    /** Layout only, without loading the image bytes */
    @Query("select new com.mjt.Football_Tournament_Management_System.dto.ResponseDtos$CollagePhotoRes("
            + "p.id, p.width, p.height, p.posX, p.posY, p.size, p.layer, p.featured) from CollagePhoto p order by p.layer, p.id")
    List<ResponseDtos.CollagePhotoRes> layout();

    @Query("select p.contentType as contentType, p.imageData as data from CollagePhoto p where p.id = :id")
    Optional<Image> image(Long id);

    @Query("select coalesce(max(p.layer), 0) from CollagePhoto p")
    int maxLayer();

    @Modifying
    @Query("update CollagePhoto p set p.featured = false where p.featured = true and p.id <> :keep")
    void clearFeaturedExcept(Long keep);

    interface Image {
        String getContentType();
        byte[] getData();
    }
}
