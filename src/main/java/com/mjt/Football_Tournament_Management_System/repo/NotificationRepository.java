package com.mjt.Football_Tournament_Management_System.repo;


import com.mjt.Football_Tournament_Management_System.entire.AppUser;
import com.mjt.Football_Tournament_Management_System.entire.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** News of the tournaments the user follows, newest first. */
    @Query("select n from Notification n where n.tournament in "
            + "(select f.tournament from Follow f where f.user = :user) order by n.createdAt desc, n.id desc")
    List<Notification> forFollower(AppUser user, Pageable page);

    @Query("select count(n) from Notification n where n.tournament in "
            + "(select f.tournament from Follow f where f.user = :user) and n.createdAt > :since")
    long countForFollowerSince(AppUser user, LocalDateTime since);
}
