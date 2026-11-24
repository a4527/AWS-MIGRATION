package com.example.fileshare.persistence;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fileshare-postgres-profile;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
class PostgresProfileIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void postgresProfileUsesJpaRepositoriesForUserAndFileMetadata() throws Exception {
        String accessToken = signupAndLogin("day5-jpa@example.com");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "jpa.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "jpa profile".getBytes()
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/files")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").isNumber())
                .andReturn();

        long fileId = objectMapper.readTree(uploadResult.getResponse().getContentAsString())
                .path("data")
                .path("fileId")
                .asLong();

        mockMvc.perform(get("/api/files/{id}", fileId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFileName").value("jpa.txt"));
    }

    @Test
    void listPagesNewestFirstWithCursorAndSkipsDeletedFiles() throws Exception {
        String token = signupAndLogin("perf02-paging@example.com");
        long[] ids = new long[5];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = upload(token, "page-" + i + ".txt");
        }
        mockMvc.perform(delete("/api/files/{id}", ids[3]).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // 보이는 파일(최신순): ids[4], ids[2], ids[1], ids[0]
        MvcResult first = mockMvc.perform(get("/api/files").param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].fileId").value(ids[4]))
                .andExpect(jsonPath("$.data.items[1].fileId").value(ids[2]))
                .andExpect(jsonPath("$.data.nextCursor").value(String.valueOf(ids[2])))
                .andReturn();
        String cursor = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("nextCursor").asText();

        mockMvc.perform(get("/api/files").param("size", "2").param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].fileId").value(ids[1]))
                .andExpect(jsonPath("$.data.items[1].fileId").value(ids[0]))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));

        // 다른 사용자의 파일은 보이지 않는다.
        String other = signupAndLogin("perf02-other@example.com");
        mockMvc.perform(get("/api/files").header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void listRejectsInvalidSizeOrCursor() throws Exception {
        String token = signupAndLogin("perf02-invalid@example.com");
        for (String[] query : new String[][] {
                {"size", "0"}, {"size", "101"}, {"size", "abc"}, {"cursor", "0"}, {"cursor", "-5"}, {"cursor", "x1"}}) {
            mockMvc.perform(get("/api/files").param(query[0], query[1])
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        }
        mockMvc.perform(get("/api/files").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private long upload(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("file", name, MediaType.TEXT_PLAIN_VALUE, name.getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("fileId").asLong();
    }

    private String signupAndLogin(String email) throws Exception {
        String password = "Password123!";
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "name": "Day 5 JPA User"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }
}
