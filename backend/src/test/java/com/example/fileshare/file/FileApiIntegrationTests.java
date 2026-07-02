package com.example.fileshare.file;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class FileApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void uploadListDownloadAndDeleteFile() throws Exception {
        String accessToken = signupAndLogin("day4-owner@example.com");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "hello day 4".getBytes()
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/files")
                        .file(file)
                        .param("description", "Day 4 report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("File uploaded"))
                .andExpect(jsonPath("$.data.originalFileName").value("report.txt"))
                .andExpect(jsonPath("$.data.status").value("available"))
                .andExpect(jsonPath("$.data.scanStatus").value("CLEAN"))
                .andReturn();

        long fileId = readFileId(uploadResult);

        mockMvc.perform(get("/api/files")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fileId").value(fileId))
                .andExpect(jsonPath("$.data[0].description").value("Day 4 report"));

        mockMvc.perform(get("/api/files/{id}/download", fileId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("report.txt")))
                .andExpect(content().string("hello day 4"));

        mockMvc.perform(delete("/api/files/{id}", fileId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").value(fileId))
                .andExpect(jsonPath("$.data.status").value("deleted"));

        mockMvc.perform(get("/api/files/{id}", fileId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidExtension() throws Exception {
        String accessToken = signupAndLogin("day4-invalid-extension@example.com");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malware.exe",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "bad".getBytes()
        );

        mockMvc.perform(multipart("/api/files")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FILE_INVALID_TYPE"));
    }

    @Test
    void otherUserCannotReadFileMetadata() throws Exception {
        String ownerToken = signupAndLogin("day4-owner-access@example.com");
        String otherToken = signupAndLogin("day4-other-access@example.com");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "private.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "private".getBytes()
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/files")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(get("/api/files/{id}", readFileId(uploadResult))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH_ACCESS_DENIED"));
    }

    private String signupAndLogin(String email) throws Exception {
        String password = "Password123!";
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "name": "Day 4 User"
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

    private long readFileId(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("fileId").asLong();
    }
}
