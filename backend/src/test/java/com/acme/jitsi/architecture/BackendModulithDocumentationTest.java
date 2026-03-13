package com.acme.jitsi.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.domains.DomainModuleTopology;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class BackendModulithDocumentationTest {

  private static final Path OUTPUT_DIRECTORY = Path.of("build", "spring-modulith-docs");

  @Test
  void generatesModuleDocumentationUnderBuildDirectory() throws IOException {
    deleteDirectoryIfPresent(OUTPUT_DIRECTORY);

    ApplicationModules modules = ApplicationModules.of(DomainModuleTopology.class).verify();

    new Documenter(modules)
        .writeModulesAsPlantUml()
        .writeIndividualModulesAsPlantUml()
        .writeModuleCanvases();

    assertThat(OUTPUT_DIRECTORY)
        .exists()
        .isDirectory();

    try (Stream<Path> paths = Files.walk(OUTPUT_DIRECTORY)) {
      List<String> generatedFiles = paths
          .filter(Files::isRegularFile)
          .map(OUTPUT_DIRECTORY::relativize)
          .map(Path::toString)
          .collect(Collectors.toList());

      assertThat(generatedFiles)
          .anyMatch(path -> path.equals("components.puml") || path.equals("modules.puml"))
          .anyMatch(path -> path.endsWith(".adoc"));
    }
  }

  private static void deleteDirectoryIfPresent(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      return;
    }

    try (Stream<Path> paths = Files.walk(directory)) {
      paths.sorted(Comparator.reverseOrder())
          .forEach(path -> {
            try {
              Files.delete(path);
            } catch (IOException ex) {
              throw new IllegalStateException("Failed to delete existing Modulith docs output: " + path, ex);
            }
          });
    }
  }
}