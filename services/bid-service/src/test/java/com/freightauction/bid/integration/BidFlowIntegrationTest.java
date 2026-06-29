package com.freightauction.bid.integration;

import com.freightauction.bid.auction.AuctionCacheService;
import com.freightauction.bid.domain.BidStatus;
import com.freightauction.bid.repository.BidRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class BidFlowIntegrationTest {

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
        registry.add("auction.service.url", () -> "http://localhost:9999");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuctionCacheService auctionCacheService;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final UUID CARRIER_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");

    private String carrierToken;
    private UUID openAuctionId;
    private UUID closedAuctionId;

    @BeforeEach
    void setUp() {
        // insere usuário fixo na tabela users (FK de bids.user_id)
        jdbcTemplate.update(
            "INSERT INTO users (id, name, email, role) VALUES (?, 'Transportadora Teste', 'carrier@test.com', 'TRANSPORTADORA') ON CONFLICT DO NOTHING",
            CARRIER_ID
        );

        carrierToken = generateToken("TRANSPORTADORA", CARRIER_ID);

        // prepara leilão aberto no Redis E na tabela auctions (FK de bids.auction_id)
        openAuctionId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO auctions (id, status) VALUES (?, 'OPEN')", openAuctionId);
        auctionCacheService.saveAsOpen(openAuctionId, new BigDecimal("1000.00"));

        // prepara leilão fechado no Redis E na tabela auctions (FK de bids.auction_id)
        closedAuctionId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO auctions (id, status) VALUES (?, 'CLOSED')", closedAuctionId);
        auctionCacheService.saveAsOpen(closedAuctionId, new BigDecimal("500.00"));
        auctionCacheService.saveAsClosed(closedAuctionId);
    }

    // -------------------------------------------------------------------------
    // POST /v1/bids
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /v1/bids deve aceitar lance válido e retornar 202")
    void shouldAcceptValidBid() throws Exception {
        mockMvc.perform(post("/v1/bids")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "auctionId": "%s",
                                  "amount": 800.00
                                }
                                """.formatted(openAuctionId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.bidId").exists())
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    @DisplayName("POST /v1/bids sem token deve retornar 401")
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(post("/v1/bids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "auctionId": "%s",
                                  "amount": 800.00
                                }
                                """.formatted(openAuctionId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /v1/bids em leilão fechado deve retornar 422")
    void shouldRejectBidOnClosedAuction() throws Exception {
        mockMvc.perform(post("/v1/bids")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "auctionId": "%s",
                                  "amount": 300.00
                                }
                                """.formatted(closedAuctionId)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("POST /v1/bids deve persistir lance no Postgres")
    void shouldPersistBidInPostgres() throws Exception {
        String response = mockMvc.perform(post("/v1/bids")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "auctionId": "%s",
                                  "amount": 750.00
                                }
                                """.formatted(openAuctionId)))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String bidIdStr = extractField(response, "bidId");
        UUID bidId = UUID.fromString(bidIdStr);

        assertThat(bidRepository.findById(bidId)).isPresent();
    }

    @Test
    @DisplayName("POST /v1/bids menor lance deve ser atualizado no Redis via consumer assíncrono")
    void shouldUpdateBestBidInRedisAfterProcessing() throws Exception {
        mockMvc.perform(post("/v1/bids")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "auctionId": "%s",
                                  "amount": 600.00
                                }
                                """.formatted(openAuctionId)))
                .andExpect(status().isAccepted());

        // aguarda o consumer RabbitMQ processar o lance e atualizar o Redis
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        mockMvc.perform(get("/v1/bids/auctions/" + openAuctionId + "/best"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.amount").value(600.00))
                );
    }

    @Test
    @DisplayName("POST /v1/bids lance processado deve ter status VALIDATED ou REJECTED no Postgres")
    void shouldUpdateBidStatusAfterProcessing() throws Exception {
        String response = mockMvc.perform(post("/v1/bids")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "auctionId": "%s",
                                  "amount": 700.00
                                }
                                """.formatted(openAuctionId)))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID bidId = UUID.fromString(extractField(response, "bidId"));

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var bid = bidRepository.findById(bidId);
                    assertThat(bid).isPresent();
                    assertThat(bid.get().getStatus())
                            .isIn(BidStatus.VALIDATED, BidStatus.REJECTED);
                });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String extractField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key) + key.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private String generateToken(String role, UUID userId) {
        try {
            String secret = "integration-test-secret-with-32-chars!!";
            String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            long exp = 9999999999L;
            String payload = """
                    {"sub":"%s","role":"%s","iat":1000000000,"exp":%d}
                    """.formatted(userId, role, exp).strip();

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