package com.kiwi.project.bpm.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.bpm.dto.BpmComponentPluginDescriptor;
import com.kiwi.project.bpm.model.BpmComponentBundleManifest;
import com.kiwi.project.bpm.service.PluginBpmComponentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BpmComponentBundleReaderTest {

  @Mock private ConfigurableApplicationContext applicationContext;
  @Mock private PluginBpmComponentProvider pluginBpmComponentProvider;

  private BpmComponentBundleReader reader;

  @BeforeEach
  void setUp() {
    lenient().when(pluginBpmComponentProvider.getSource()).thenReturn("plugin");
    when(applicationContext.getClassLoader()).thenReturn(getClass().getClassLoader());
    reader = new BpmComponentBundleReader(new ObjectMapper(), applicationContext, pluginBpmComponentProvider);
  }

  @Test
  void describeJar_withoutBundle_fallsBackToScan(@TempDir Path tempDir) throws IOException {
    Path exampleJar = resolveExampleJar();
    Path jar = tempDir.resolve("demo-no-bundle.jar");
    copyJarWithoutBundleEntry(exampleJar, jar);

    BpmComponentPluginDescriptor descriptor = reader.describeJar(jar);

    assertEquals("demo-no-bundle", descriptor.getBundle().getName());
    assertFalse(descriptor.getComponents().isEmpty());
    assertEquals("demoGreeting", descriptor.getComponents().get(0).getKey());
    assertEquals("plugin_demoGreeting", descriptor.getComponents().get(0).getComponentId());
    assertEquals("scanned", descriptor.getComponents().get(0).getSource());
  }

  @Test
  void describeJar_withValidBundle(@TempDir Path tempDir) throws IOException {
    Path exampleJar = resolveExampleJar();
    Path jar = tempDir.resolve("demo-with-bundle.jar");
    Files.copy(exampleJar, jar);

    BpmComponentPluginDescriptor descriptor = reader.describeJar(jar);

    assertEquals("Kiwi 组件示例包", descriptor.getBundle().getName());
    assertEquals("1.0.0", descriptor.getBundle().getVersion());
    assertEquals(1, descriptor.getComponents().size());
    assertEquals("manifest", descriptor.getComponents().get(0).getSource());
    assertTrue(descriptor.getSha256().length() >= 64);
    assertTrue(descriptor.getFileSizeBytes() > 0);
  }

  @Test
  void describeJar_keyMismatch_throws(@TempDir Path tempDir) throws IOException {
    Path exampleJar = resolveExampleJar();
    Path jar = tempDir.resolve("demo-bad-bundle.jar");
    copyJarWithBundle(
        exampleJar,
        jar,
        """
        {
          "schemaVersion": "1",
          "name": "Bad",
          "version": "1.0.0",
          "components": [
            { "key": "nonExistent", "name": "Ghost" }
          ]
        }
        """);

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> reader.describeJar(jar));
    assertTrue(ex.getMessage().contains("nonExistent"));
  }

  private Path resolveExampleJar() {
    String jarName = "kiwi-bpmn-component-example-1.0.0-SNAPSHOT.jar";
    List<Path> candidates =
        List.of(
            Path.of("").toAbsolutePath().resolve("../../kiwi-bpmn/kiwi-bpmn-component-example/target/" + jarName).normalize(),
            Path.of("").toAbsolutePath().resolve("../../../kiwi-bpmn/kiwi-bpmn-component-example/target/" + jarName).normalize(),
            Path.of("").toAbsolutePath().resolve("kiwi-bpmn/kiwi-bpmn-component-example/target/" + jarName).normalize());
    for (Path candidate : candidates) {
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException(
        "请先构建 example 模块: mvn -pl kiwi-bpmn/kiwi-bpmn-component-example -am package -DskipTests");
  }

  private void copyJarWithoutBundleEntry(Path source, Path target) throws IOException {
    try (var jis = new java.util.jar.JarFile(source.toFile());
        var jos = new JarOutputStream(Files.newOutputStream(target))) {
      jis.stream()
          .filter(
              e ->
                  !e.getName().equals(BpmComponentBundleManifest.BundleResourcePath))
          .forEach(
              e -> {
                try {
                  jos.putNextEntry(new JarEntry(e.getName()));
                  try (var in = jis.getInputStream(e)) {
                    in.transferTo(jos);
                  }
                  jos.closeEntry();
                } catch (IOException io) {
                  throw new java.io.UncheckedIOException(io);
                }
              });
    }
  }

  private void copyJarWithBundle(Path source, Path target, String bundleJson) throws IOException {
    try (var jis = new java.util.jar.JarFile(source.toFile());
        var jos = new JarOutputStream(Files.newOutputStream(target))) {
      jis.stream()
          .filter(
              e ->
                  !e.getName().equals(BpmComponentBundleManifest.BundleResourcePath))
          .forEach(
              e -> {
                try {
                  jos.putNextEntry(new JarEntry(e.getName()));
                  try (var in = jis.getInputStream(e)) {
                    in.transferTo(jos);
                  }
                  jos.closeEntry();
                } catch (IOException io) {
                  throw new java.io.UncheckedIOException(io);
                }
              });
      jos.putNextEntry(new JarEntry(BpmComponentBundleManifest.BundleResourcePath));
      jos.write(bundleJson.getBytes(StandardCharsets.UTF_8));
      jos.closeEntry();
    }
  }
}
