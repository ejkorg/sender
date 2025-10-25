package com.onsemi.cim.apps.exensio.exensioDearchiver;

import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.PayloadCandidate;
import com.onsemi.cim.apps.exensio.exensioDearchiver.service.RefDbService;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.StageStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class StageStatusUserScopingTest {

    @Autowired
    private RefDbService refDbService;

    @Autowired
    private ExensioDearchiveController controller;

    @AfterEach
    public void cleanupAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void adminSeesAll_and_nonAdminSeesOnlyTheirRecords() {
        String site = "TEST_SITE";
        int senderId = 42;

        // Stage 2 payloads for alice and 1 for bob
        refDbService.stagePayloads(site, senderId, "alice", List.of(
                new PayloadCandidate("m1", "d1"),
                new PayloadCandidate("m2", "d2")
        ), true);

        refDbService.stagePayloads(site, senderId, "bob", List.of(
                new PayloadCandidate("m3", "d3")
        ), true);

        // Admin context -> should see total = 3 and both users in breakdown
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );
        List<StageStatus> adminStatuses = controller.getStageStatus();
        assertThat(adminStatuses).isNotEmpty();
        StageStatus adminForSite = adminStatuses.stream().filter(s -> s.site().equals(site) && s.senderId() == senderId).findFirst().orElseThrow();
        assertThat(adminForSite.total()).isEqualTo(3L);
        assertThat(adminForSite.users()).anyMatch(u -> u.username().equalsIgnoreCase("alice") && u.total() == 2L);
        assertThat(adminForSite.users()).anyMatch(u -> u.username().equalsIgnoreCase("bob") && u.total() == 1L);

        // Non-admin alice -> should see only alice's records (total = 2)
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
        List<StageStatus> aliceStatuses = controller.getStageStatus();
        assertThat(aliceStatuses).isNotEmpty();
        StageStatus aliceForSite = aliceStatuses.stream().filter(s -> s.site().equals(site) && s.senderId() == senderId).findFirst().orElseThrow();
        assertThat(aliceForSite.total()).isEqualTo(2L);
        assertThat(aliceForSite.users()).allMatch(u -> u.username().equalsIgnoreCase("alice"));
    }
}
