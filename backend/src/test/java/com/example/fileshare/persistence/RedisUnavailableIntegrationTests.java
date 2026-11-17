package com.example.fileshare.persistence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** redis 프로필에서 Redis가 응답하지 않아도(사용하지 않는 포트) 파일 API가 DB 기준으로 동작하는지 확인한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fileshare-redis-down;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6399",
        "spring.data.redis.timeout=500ms",
        "spring.data.redis.connect-timeout=500ms"
})
@AutoConfigureMockMvc
@ActiveProfiles({"postgres", "redis"})
class RedisUnavailableIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fileApiWorksWhenRedisIsDown() throws Exception {
        String token = signupAndLogin("redis-down@example.com");
        MockMultipartFile file = new MockMultipartFile("file", "cache.txt", MediaType.TEXT_PLAIN_VALUE, "no cache".getBytes());

        MvcResult upload = mockMvc.perform(multipart("/api/files").file(file).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        long fileId = objectMapper.readTree(upload.getResponse().getContentAsString()).path("data").path("fileId").asLong();

        mockMvc.perform(get("/api/files/{id}", fileId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").value(fileId));
        mockMvc.perform(get("/api/files/{id}/download", fileId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().bytes("no cache".getBytes()));
        mockMvc.perform(delete("/api/files/{id}", fileId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/files/{id}", fileId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/files/{id}/download", fileId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String signupAndLogin(String email) throws Exception {
        String password = "Password123!";
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s", "name": "Redis Down"}
                                """.formatted(email, password)))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
}
