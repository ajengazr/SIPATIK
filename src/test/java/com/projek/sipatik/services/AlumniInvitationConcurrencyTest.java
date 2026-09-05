package com.projek.sipatik.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.projek.sipatik.dto.UserRequest;
import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.AlumniInvitationRepository;
import com.projek.sipatik.repositories.UserRepository;

@SpringBootTest
class AlumniInvitationConcurrencyTest {
    @Autowired private AlumniInvitationService invitationService;
    @Autowired private AlumniInvitationRepository invitations;
    @Autowired private UserRepository users;

    @Test
    void satuTokenTidakDapatMengaktifkanAkunDuaKaliSecaraParalel() throws Exception {
        String marker = UUID.randomUUID().toString();
        Users candidate = users.saveAndFlush(Users.builder()
                .nama("Kandidat Paralel " + marker)
                .angkatan(20L)
                .jenjang("S1")
                .role(Role.USER)
                .build());
        var issued = invitationService.issue(candidate.getId(), "admin@example.org");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(registrationAttempt(
                    issued.token(), "first-" + marker + "@example.org", "081234567891", ready, start));
            Future<Boolean> second = executor.submit(registrationAttempt(
                    issued.token(), "second-" + marker + "@example.org", "081234567892", ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Boolean> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(outcomes).containsExactlyInAnyOrder(true, false);
            Users activated = users.findById(candidate.getId()).orElseThrow();
            assertThat(activated.getEmail()).isIn(
                    "first-" + marker + "@example.org",
                    "second-" + marker + "@example.org");
            assertThat(invitations.findByUserId(candidate.getId()).orElseThrow().getUsedAt()).isNotNull();
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            invitations.deleteByUserId(candidate.getId());
            invitations.flush();
            users.deleteById(candidate.getId());
            users.flush();
        }
    }

    private Callable<Boolean> registrationAttempt(String token, String email, String phone,
            CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            UserRequest request = UserRequest.builder()
                    .email(email)
                    .password("Password123")
                    .nomorHp(phone)
                    .jenjang("S1")
                    .build();
            try {
                return invitationService.register(token, request, new HashMap<>()) != null;
            } catch (IllegalArgumentException | IllegalStateException expectedReplayRejection) {
                return false;
            }
        };
    }
}
