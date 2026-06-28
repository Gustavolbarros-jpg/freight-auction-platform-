package com.freightauction.auction.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class AuctionIntegrationTest {

    static {
        System.setProperty("docker.api.version", "1.41");
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("freight_auction")
            .withUsername("freight_user")
            .withPassword("example");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management")
            .withUser("user", "password")
            .withPermission("/", "user", ".*", ".*", ".*");

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7")
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "user");
        registry.add("spring.rabbitmq.password", () -> "password");
        registry.add("spring.mongodb.uri", () ->
                "mongodb://" + mongo.getHost() + ":" + mongo.getMappedPort(27017) + "/audit_db");
        registry.add("auth.jwt.secret", () -> "integration-test-secret-with-32-chars!!");
        registry.add("bid.service.url", () -> "http://localhost:9999");
    }

    @Autowired
    private MockMvc mockMvc;

    private String adminToken;

    @BeforeEach
    void setUp() {
        adminToken = generateAdminToken();
    }

    // -------------------------------------------------------------------------
    // POST /v1/loads
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /v1/loads deve criar carga e retornar 201")
    void shouldCreateLoad() throws Exception {
        mockMvc.perform(post("/v1/loads")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "origin": "Recife",
                                  "destination": "Olinda",
                                  "description": "Carga teste integração",
                                  "weightKg": 100,
                                  "initialPrice": 1000.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.origin").value("Recife"))
                .andExpect(jsonPath("$.destination").value("Olinda"));
    }

    @Test
    @DisplayName("POST /v1/loads sem token deve retornar 401")
    void shouldReturn401WhenCreatingLoadWithoutToken() throws Exception {
        mockMvc.perform(post("/v1/loads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "origin": "Recife",
                                  "destination": "Olinda",
                                  "description": "Carga teste",
                                  "weightKg": 100,
                                  "initialPrice": 1000.00
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /v1/loads com body inválido deve retornar 400")
    void shouldReturn400WhenCreatingLoadWithInvalidBody() throws Exception {
        mockMvc.perform(post("/v1/loads")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // POST /v1/auctions
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /v1/auctions deve criar leilão para load existente")
    void shouldCreateAuction() throws Exception {
        String loadId = createLoad();

        mockMvc.perform(post("/v1/auctions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loadId": "%s",
                                  "durationMinutes": 30
                                }
                                """.formatted(loadId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("POST /v1/auctions com loadId inexistente deve retornar 400")
    void shouldReturn400WhenAuctionLoadNotFound() throws Exception {
        mockMvc.perform(post("/v1/auctions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loadId": "00000000-0000-0000-0000-000000000099",
                                  "durationMinutes": 30
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /v1/auctions com load já em leilão aberto deve retornar 409")
    void shouldReturn409WhenLoadAlreadyHasOpenAuction() throws Exception {
        String loadId = createLoad();
        createAuction(loadId);

        mockMvc.perform(post("/v1/auctions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loadId": "%s",
                                  "durationMinutes": 30
                                }
                                """.formatted(loadId)))
                .andExpect(status().isConflict());
    }

    // -------------------------------------------------------------------------
    // PATCH /v1/auctions/{id}/close
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PATCH /v1/auctions/{id}/close deve fechar leilão e retornar 200")
    void shouldCloseAuction() throws Exception {
        String loadId = createLoad();
        String auctionId = createAuction(loadId);

        mockMvc.perform(patch("/v1/auctions/" + auctionId + "/close")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").exists());
    }

    @Test
    @DisplayName("PATCH /v1/auctions/{id}/close em leilão já fechado deve retornar 409")
    void shouldReturn409WhenClosingAlreadyClosedAuction() throws Exception {
        String loadId = createLoad();
        String auctionId = createAuction(loadId);
        closeAuction(auctionId);

        mockMvc.perform(patch("/v1/auctions/" + auctionId + "/close")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String createLoad() throws Exception {
        String response = mockMvc.perform(post("/v1/loads")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "origin": "Recife",
                                  "destination": "Olinda",
                                  "description": "Carga helper",
                                  "weightKg": 50,
                                  "initialPrice": 500.00
                                }
                                """))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return extractId(response);
    }

    private String createAuction(String loadId) throws Exception {
        String response = mockMvc.perform(post("/v1/auctions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loadId": "%s",
                                  "durationMinutes": 30
                                }
                                """.formatted(loadId)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return extractId(response);
    }

    private void closeAuction(String auctionId) throws Exception {
        mockMvc.perform(patch("/v1/auctions/" + auctionId + "/close")
                .header("Authorization", "Bearer " + adminToken));
    }

    private String extractId(String json) {
        int start = json.indexOf("\"id\":\"") + 6;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private String generateAdminToken() {
        try {
            String secret = "integration-test-secret-with-32-chars!!";
            String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            long exp = 9999999999L;
            String payload = """
                    {"sub":"00000000-0000-0000-0000-000000000001","role":"ADMIN","iat":1000000000,"exp":%d}
                    """.formatted(exp).strip();

            String encodedHeader = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(header.getBytes());
            String encodedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.getBytes());
            String unsigned = encodedHeader + "." + encodedPayload;

            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(unsigned.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

            return unsigned + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar token de teste", e);
        }
    }
}