package com.example.fileshare.file;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** 실제 서블릿 컨테이너의 multipart 한도가 업로드 제한(10MB)과 맞는지 확인한다. MockMvc는 이 한도를 거치지 않는다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class UploadSizeLimitIntegrationTests {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void filesBetweenOneAndTenMegabytesAreAcceptedAndLargerOnesGetFileTooLarge() throws Exception {
        String token = signupAndLogin("size-limit@example.com");

        ResponseEntity<String> twoMegabytes = upload(token, 2 * 1024 * 1024);
        assertThat(twoMegabytes.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> overLimit = upload(token, 10 * 1024 * 1024 + 512 * 1024);
        assertThat(overLimit.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(overLimit.getBody()).path("errorCode").asText()).isEqualTo("FILE_TOO_LARGE");
    }

    private ResponseEntity<String> upload(String token, int size) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(new byte[size]) {
            @Override
            public String getFilename() {
                return "size.txt";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        return rest.postForEntity("/api/files", new HttpEntity<>(body, headers), String.class);
    }

    private String signupAndLogin(String email) throws Exception {
        Map<String, String> signup = Map.of("email", email, "password", "Password123!", "name", "Size Limit");
        assertThat(rest.postForEntity("/api/auth/signup", signup, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> login = rest.postForEntity("/api/auth/login",
                Map.of("email", email, "password", "Password123!"), String.class);
        JsonNode root = objectMapper.readTree(login.getBody());
        return root.path("data").path("accessToken").asText();
    }
}
