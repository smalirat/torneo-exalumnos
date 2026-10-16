package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.AppUser;
import com.mjt.Football_Tournament_Management_System.entire.Follow;
import com.mjt.Football_Tournament_Management_System.entire.Notification;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.AppUserRepository;
import com.mjt.Football_Tournament_Management_System.repo.FollowRepository;
import com.mjt.Football_Tournament_Management_System.repo.NotificationRepository;
import com.mjt.Football_Tournament_Management_System.repo.TournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Followed tournaments and the notifications their followers receive. */
@Service
@RequiredArgsConstructor
public class FollowService {
    static final int NOTIFICATION_PAGE = 50;
    private static final LocalDateTime NEVER = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final FollowRepository followRepo;
    private final NotificationRepository notificationRepo;
    private final AppUserRepository userRepo;
    private final TournamentRepository tournamentRepo;

    public List<ResponseDtos.TournamentRes> followed(String username) {
        return followRepo.findByUserOrderByCreatedAtDesc(user(username)).stream()
                .map(f -> ResponseDtos.TournamentRes.from(f.getTournament())).toList();
    }

    /** Idempotent: following twice keeps a single follow. */
    @Transactional
    public ResponseDtos.FollowStatusRes follow(String username, Long tournamentId) {
        var user = user(username);
        var t = tournament(tournamentId);
        if (!followRepo.existsByUserAndTournament(user, t)) followRepo.save(new Follow(null, user, t, LocalDateTime.now()));
        return new ResponseDtos.FollowStatusRes(followRepo.countByTournament(t), true);
    }

    @Transactional
    public ResponseDtos.FollowStatusRes unfollow(String username, Long tournamentId) {
        var user = user(username);
        var t = tournament(tournamentId);
        followRepo.findByUserAndTournament(user, t).ifPresent(followRepo::delete);
        followRepo.flush();
        return new ResponseDtos.FollowStatusRes(followRepo.countByTournament(t), false);
    }

    /** Public: works without a session (following = false). */
    public ResponseDtos.FollowStatusRes status(Long tournamentId, Authentication auth) {
        var t = tournament(tournamentId);
        boolean following = loggedIn(auth) && userRepo.findByUsername(auth.getName())
                .map(u -> followRepo.existsByUserAndTournament(u, t)).orElse(false);
        return new ResponseDtos.FollowStatusRes(followRepo.countByTournament(t), following);
    }

    public ResponseDtos.NotificationsRes notifications(String username) {
        var user = user(username);
        var seenAt = user.getNotificationsSeenAt();
        var items = notificationRepo.forFollower(user, PageRequest.of(0, NOTIFICATION_PAGE)).stream()
                .map(n -> ResponseDtos.NotificationRes.from(n, seenAt)).toList();
        long unread = notificationRepo.countForFollowerSince(user, seenAt == null ? NEVER : seenAt);
        return new ResponseDtos.NotificationsRes(unread, items);
    }

    @Transactional
    public void markSeen(String username) {
        user(username).setNotificationsSeenAt(LocalDateTime.now());
    }

    /** Records a piece of news of the tournament for its followers. */
    @Transactional
    public void notify(Tournament t, Notification.Type type, String message) {
        notificationRepo.save(new Notification(null, t, type, message, LocalDateTime.now()));
    }

    private static boolean loggedIn(Authentication auth) {
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    private AppUser user(String username) {
        return userRepo.findByUsername(username).orElseThrow(() -> NotFoundException.of("Usuario", username));
    }

    private Tournament tournament(Long id) {
        return tournamentRepo.findById(id).orElseThrow(() -> NotFoundException.of("Campeonato", id));
    }
}
