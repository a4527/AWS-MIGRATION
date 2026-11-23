package com.example.fileshare.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fileshare.file.domain.UploadRequest;
import com.example.fileshare.file.repository.UploadRequestRepository;
import com.example.fileshare.user.domain.User;
import com.example.fileshare.user.domain.UserRole;
import com.example.fileshare.user.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** postgres 프로필(JPA 어댑터)로 멱등 업로드·업로드 요청 조건부 갱신·관리자 점검 API를 확인한다. DB는 H2 PostgreSQL 모드다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fileshare-idempotency;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
class IdempotentUploadIntegrationTests {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UploadRequestRepository uploadRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void sameKeyReplaysAndDifferentRequestIsRejected() throws Exception {
        String token = signupAndLogin("idem-user@example.com");

        long firstId = upload(token, "key-1", "hello")
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andReturnFileId();
        long secondId = upload(token, "key-1", "hello")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturnFileId();
        assertThat(secondId).isEqualTo(firstId);

        upload(token, "key-1", "different")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_MISMATCH"));
        upload(token, "bad key", "hello")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void jpaAdapterGuardsRestartAndLateCompletion() {
        Instant now = Instant.now();
        UploadRequest started = uploadRequestRepository.insert(
                UploadRequest.start(999L, "jpa-key", "hash", "old-path", now, now.plusSeconds(60)));
        UploadRequest restarted = started.restarted("hash", "new-path", now, now.plusSeconds(60));

        assertThat(uploadRequestRepository.restart(started, restarted)).isTrue();
        assertThat(uploadRequestRepository.restart(started, restarted)).isFalse();
        assertThat(uploadRequestRepository.complete(started.id(), "old-path", 1L, now)).isFalse();
        assertThat(uploadRequestRepository.complete(started.id(), "new-path", 1L, now)).isTrue();
        assertThat(uploadRequestRepository.deleteExpired(now.plusSeconds(120))).isGreaterThanOrEqualTo(1);
        assertThat(uploadRequestRepository.find(999L, "jpa-key")).isEmpty();
    }

    @Test
    void orphanScanIsAdminOnly() throws Exception {
        String userToken = signupAndLogin("idem-normal@example.com");
        mockMvc.perform(get("/api/admin/storage/orphans").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());

        Instant now = Instant.now();
        userRepository.save(new User(null, "idem-admin@example.com", passwordEncoder.encode(PASSWORD), "Admin",
                UserRole.ADMIN, now, now));
        String adminToken = login("idem-admin@example.com");
        mockMvc.perform(get("/api/admin/storage/orphans").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minAgeMinutes").value(60))
                .andExpect(jsonPath("$.data.orphanObjects").isArray());
        mockMvc.perform(get("/api/admin/storage/orphans").param("minAgeMinutes", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    private UploadCall upload(String token, String key, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "idem.txt", MediaType.TEXT_PLAIN_VALUE, content.getBytes());
        return new UploadCall(mockMvc.perform(multipart("/api/files")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("Idempotency-Key", key)));
    }

    private final class UploadCall {

        private final org.springframework.test.web.servlet.ResultActions actions;

        UploadCall(org.springframework.test.web.servlet.ResultActions actions) {
            this.actions = actions;
        }

        UploadCall andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            actions.andExpect(matcher);
            return this;
        }

        long andReturnFileId() throws Exception {
            MvcResult result = actions.andReturn();
            return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("fileId").asLong();
        }
    }

    private String signupAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s", "name": "Idempotency User"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk());
        return login(email);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
}
