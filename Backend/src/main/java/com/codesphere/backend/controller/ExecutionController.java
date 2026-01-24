package com.codesphere.backend.controller;

import com.codesphere.backend.dto.ApiResponse;
import com.codesphere.backend.dto.ExecuteRequest;
import com.codesphere.backend.dto.ExecutionResult;
import com.codesphere.backend.entity.ExecutionEntity;
import com.codesphere.backend.entity.ProjectEntity;
import com.codesphere.backend.repository.ExecutionRepository;
import com.codesphere.backend.repository.ProjectRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/projects/{projectName}")
public class ExecutionController {

    private static final String BASE_DIR = "codesphere_workspace";

    private final ProjectRepository projectRepository;
    private final ExecutionRepository executionRepository;

    public ExecutionController(ProjectRepository projectRepository,
                               ExecutionRepository executionRepository) {
        this.projectRepository = projectRepository;
        this.executionRepository = executionRepository;
    }

    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<String>> executeCode(
            @PathVariable String projectName,
            @RequestBody ExecuteRequest request) {

        try {
            // 1️⃣ Fetch project
            ProjectEntity project = projectRepository.findByName(projectName)
                    .orElseThrow(() -> new RuntimeException("Project not found"));

            // 2️⃣ Build paths
            Path projectPath = Path.of(BASE_DIR, projectName);
            Path filePath = projectPath.resolve(request.getFilename());

            if (!Files.exists(filePath)) {
                throw new RuntimeException("File not found");
            }

            // 3️⃣ Detect language
            String extension = getExtension(request.getFilename());

            // 4️⃣ Execute
            ExecutionResult result;
            switch (extension) {
                case "java" -> result = executeJava(projectPath, filePath, request.getInput());
                case "py" -> result = executePython(filePath, request.getInput());
                default -> throw new RuntimeException("Unsupported file type");
            }

            // 5️⃣ Persist execution history
            ExecutionEntity execution = new ExecutionEntity();
            execution.setProject(project);
            execution.setFilename(request.getFilename());
            execution.setStatus(result.getStatus());
            execution.setOutput(result.getOutput());
            execution.setError(result.getError());

            executionRepository.save(execution);

            // 6️⃣ API response
            return ResponseEntity.ok(
                    new ApiResponse<>(
                            true,
                            "Execution completed",
                            result.getStatus().equals("SUCCESS")
                                    ? result.getOutput()
                                    : result.getError()
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    // ---------- helpers ----------

    private String getExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index == -1 ? "" : filename.substring(index + 1).toLowerCase();
    }

    private ExecutionResult executeJava(Path projectPath,
                                        Path filePath,
                                        String input) throws Exception {

        // Compile
        Process compile = new ProcessBuilder(
                "javac", filePath.getFileName().toString())
                .directory(projectPath.toFile())
                .redirectErrorStream(true)
                .start();

        compile.waitFor(5, TimeUnit.SECONDS);
        String compileOutput = new String(compile.getInputStream().readAllBytes());

        if (compile.exitValue() != 0) {
            return new ExecutionResult(null, compileOutput, "ERROR");
        }

        // Run
        String className = filePath.getFileName().toString().replace(".java", "");
        Process run = new ProcessBuilder("java", className)
                .directory(projectPath.toFile())
                .redirectErrorStream(true)
                .start();

        if (input != null && !input.isBlank()) {
            try (BufferedWriter writer =
                         new BufferedWriter(new OutputStreamWriter(run.getOutputStream()))) {
                writer.write(input);
                writer.flush();
            }
        }

        boolean finished = run.waitFor(5, TimeUnit.SECONDS);
        if (!finished) {
            run.destroyForcibly();
            return new ExecutionResult(null, "Execution timed out", "TIMEOUT");
        }

        String runOutput = new String(run.getInputStream().readAllBytes());
        return new ExecutionResult(runOutput, null, "SUCCESS");
    }

    private ExecutionResult executePython(Path filePath, String input) throws Exception {

        Process run = new ProcessBuilder("python3", filePath.toString())
                .redirectErrorStream(true)
                .start();

        if (input != null && !input.isBlank()) {
            try (BufferedWriter writer =
                         new BufferedWriter(new OutputStreamWriter(run.getOutputStream()))) {
                writer.write(input);
                writer.flush();
            }
        }

        boolean finished = run.waitFor(5, TimeUnit.SECONDS);
        if (!finished) {
            run.destroyForcibly();
            return new ExecutionResult(null, "Execution timed out", "TIMEOUT");
        }

        String output = new String(run.getInputStream().readAllBytes());

        if (run.exitValue() != 0) {
            return new ExecutionResult(null, output, "ERROR");
        }

        return new ExecutionResult(output, null, "SUCCESS");
    }
}
