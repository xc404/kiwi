package com.kiwi.project.bpm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.bpm.config.BpmRemoteMarketProperties;
import com.kiwi.project.bpm.dao.BpmComponentDao;
import com.kiwi.project.bpm.dto.BpmRemoteMarketItemDto;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.utils.KiwiVersionCompatibilityHelper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BpmRemoteMarketServiceTest {

  @Mock private BpmComponentDao componentDao;

  private HttpServer server;
  private int port;
  private BpmRemoteMarketService marketService;
  private BpmRemoteMarketDownloadService downloadService;
  private byte[] templateZip;
  private String templateSha256;

  @BeforeEach
  void setUp() throws Exception {
    templateZip = buildDemoTemplateZip();
    templateSha256 = sha256Hex(templateZip);
    String indexJson =
        """
        {
          "schemaVersion": 1,
          "generatedAt": "2026-07-08T00:00:00Z",
          "items": [
            {
              "type": "template",
              "slug": "demo-hello",
              "name": "Hello Demo",
              "version": "1.0.0",
              "summary": "test",
              "kiwiMinVersion": "1.0.0",
              "downloadUrl": "templates/demo-hello/1.0.0/demo-hello-1.0.0.kiwi-template-pack",
              "sha256": "%s",
              "manifestUrl": "templates/demo-hello/1.0.0/manifest.json"
            }
          ]
        }
        """
            .formatted(templateSha256);

    server = HttpServer.create(new InetSocketAddress(0), 0);
    port = server.getAddress().getPort();
    server.createContext("/market/index.json", exchange -> {
      byte[] body = indexJson.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(body);
      }
    });
    server.createContext(
        "/templates/demo-hello/1.0.0/demo-hello-1.0.0.kiwi-template-pack",
        exchange -> {
          exchange.sendResponseHeaders(200, templateZip.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(templateZip);
          }
        });
    server.createContext(
        "/templates/demo-hello/1.0.0/manifest.json",
        exchange -> {
          byte[] body = "{\"slug\":\"demo-hello\"}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();

    BpmRemoteMarketProperties properties = new BpmRemoteMarketProperties();
    properties.setEnabled(true);
    properties.setKiwiVersion("1.0.0-SNAPSHOT");
    properties.setCacheTtlSeconds(60);
    BpmRemoteMarketProperties.Source source = new BpmRemoteMarketProperties.Source();
    source.setId("test");
    source.setName("Test");
    source.setBaseUrl("http://localhost:" + port + "/");
    source.setIndexPath("market/index.json");
    properties.setSources(List.of(source));

    ObjectMapper objectMapper = new ObjectMapper();
    BpmRemoteMarketHttpFetcher fetcher = new BpmRemoteMarketHttpFetcher();
    KiwiVersionCompatibilityHelper versionHelper = new KiwiVersionCompatibilityHelper();
    marketService = new BpmRemoteMarketService(properties, fetcher, objectMapper, componentDao, versionHelper);
    downloadService = new BpmRemoteMarketDownloadService(fetcher, marketService);
    when(componentDao.findAll()).thenReturn(List.of());
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void listAndDownloadVerified() throws Exception {
    List<BpmRemoteMarketItemDto> items = marketService.listItems("template", null, null);
    assertEquals(1, items.size());
    BpmRemoteMarketItemDto item = items.get(0);
    assertEquals("demo-hello", item.getSlug());
    assertTrue(item.isKiwiCompatible());

    byte[] downloaded = downloadService.downloadVerified(item);
    assertEquals(templateSha256, sha256Hex(downloaded));
  }

  @Test
  void getDetailLoadsManifest() {
    var detail = marketService.getItem("demo-hello", "1.0.0", null);
    assertEquals("Hello Demo", detail.getName());
    assertEquals("demo-hello", detail.getManifest().get("slug"));
  }

  @Test
  void syncRefreshesCache() {
    var result = marketService.sync();
    assertEquals(1, result.getSourceCount());
    assertEquals(1, result.getItemCount());
    assertTrue(result.getFetchedAt() > 0);
  }

  private static byte[] buildDemoTemplateZip() throws IOException {
    Path temp = Files.createTempFile("demo-pack-", ".zip");
  try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(temp))) {
      writeEntry(zos, "manifest.json", "{\"name\":\"Hello\",\"version\":\"1.0.0\",\"slug\":\"demo-hello\"}");
      writeEntry(zos, "env-vars.json", "[]");
      writeEntry(
          zos,
          "processes/hello.bpmn",
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="D1" targetNamespace="http://bpmn.io/schema/bpmn">
            <bpmn:process id="hello" name="Hello" isExecutable="true">
              <bpmn:startEvent id="StartEvent_1"/>
            </bpmn:process>
          </bpmn:definitions>
          """);
    }
    byte[] bytes = Files.readAllBytes(temp);
    Files.deleteIfExists(temp);
    return bytes;
  }

  private static void writeEntry(ZipOutputStream zos, String name, String content) throws IOException {
    zos.putNextEntry(new ZipEntry(name));
    zos.write(content.getBytes(StandardCharsets.UTF_8));
    zos.closeEntry();
  }

  private static String sha256Hex(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
}
