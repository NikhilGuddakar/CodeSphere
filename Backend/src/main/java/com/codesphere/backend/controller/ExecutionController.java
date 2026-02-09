package com.codesphere.backend.controller;

import com.codesphere.backend.dto.ApiResponse;
import com.codesphere.backend.dto.ExecuteRequest;
import com.codesphere.backend.dto.ExecutionResult;
import com.codesphere.backend.entity.ExecutionEntity;
import com.codesphere.backend.entity.ProjectEntity;
import com.codesphere.backend.entity.UserEntity;
import com.codesphere.backend.repository.ExecutionRepository;
import com.codesphere.backend.repository.ProjectRepository;
import com.codesphere.backend.repository.UserRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import com.codesphere.backend.util.WorkspacePaths;

@RestController
@RequestMapping("/api/projects/{projectName}")
public class ExecutionController {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ExecutionRepository executionRepository;

    public ExecutionController(ProjectRepository projectRepository,
                               UserRepository userRepository,
                               ExecutionRepository executionRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.executionRepository = executionRepository;
    }

    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<String>> executeCode(
            @PathVariable String projectName,
            @Valid @RequestBody ExecuteRequest request) {

        if (!WorkspacePaths.isSafeProjectName(projectName)) {
            return ResponseEntity.badRequest()
                .body(new ApiResponse<>(false, "Invalid project name", null));
        }
        if (request == null || request.getFilename() == null || request.getFilename().isBlank()) {
            return ResponseEntity.badRequest()
                .body(new ApiResponse<>(false, "Filename is required", null));
        }
        if (!WorkspacePaths.isSafeFilename(request.getFilename())) {
            return ResponseEntity.badRequest()
                .body(new ApiResponse<>(false, "Invalid filename", null));
        }
        if (request.getInput() != null
            && request.getInput().getBytes().length > WorkspacePaths.MAX_CONTENT_BYTES) {
            return ResponseEntity.badRequest()
                .body(new ApiResponse<>(false, "Input too large", null));
        }

        // 1️⃣ Get logged-in user
        String username = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 2️⃣ Validate project ownership
        ProjectEntity project = projectRepository
                .findByNameAndUser(projectName, user)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        try {
            Path projectPath = WorkspacePaths.projectDir(user, projectName);
            Path filePath = projectPath.resolve(request.getFilename()).normalize();
            if (!filePath.startsWith(projectPath)) {
                return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Invalid filename path", null));
            }
            if (!Files.exists(filePath)) {
                return ResponseEntity.status(404)
                    .body(new ApiResponse<>(false, "File not found", null));
            }

            String extension = getExtension(request.getFilename());
            ExecutionResult result;

            if ("java".equals(extension)) {
                result = executeJava(projectPath, filePath, request.getInput());
            } else if ("py".equals(extension)) {
                result = executePython(filePath, request.getInput());
            } else if ("js".equals(extension)) {
                result = executeNode(filePath, request.getInput());
            } else if ("c".equals(extension)) {
                result = executeC(projectPath, filePath, request.getInput());
            } else if ("cpp".equals(extension) || "cc".equals(extension) || "cxx".equals(extension)) {
                result = executeCpp(projectPath, filePath, request.getInput());
            } else if ("go".equals(extension)) {
                result = executeGo(projectPath, filePath, request.getInput());
            } else if ("cs".equals(extension)) {
                result = executeCSharp(projectPath, filePath, request.getInput());
            } else if ("html".equals(extension) || "css".equals(extension)) {
                return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Use frontend preview for web files", null));
            } else {
                return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Unsupported file type", null));
            }

            // 3️⃣ Store execution result in DB
            ExecutionEntity execution = new ExecutionEntity();
            execution.setProject(project);
            execution.setFilename(request.getFilename());
            execution.setStatus(result.getStatus());
            execution.setOutput(result.getOutput());
            execution.setError(result.getError());

            executionRepository.save(execution);

            return ResponseEntity.ok(
                    new ApiResponse<>(
                            true,
                            "Execution completed",
                            "SUCCESS".equals(result.getStatus())
                                    ? result.getOutput()
                                    : result.getError()
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse<>(false, "Execution failed: " + e.getMessage(), null));
        }
    }

    // ---------------- helpers ----------------

    private String getExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index == -1 ? "" : filename.substring(index + 1).toLowerCase();
    }

    private ExecutionResult executeJava(Path projectPath,
                                        Path filePath,
                                        String input) throws Exception {
        if (!commandAvailable("javac")) {
            return new ExecutionResult(null, "javac is not installed on server", "ERROR");
        }
        if (!commandAvailable("java")) {
            return new ExecutionResult(null, "java runtime is not installed on server", "ERROR");
        }

        Process compile = new ProcessBuilder(
                "javac", filePath.getFileName().toString())
                .directory(projectPath.toFile())
                .redirectErrorStream(true)
                .start();

        boolean compiled = compile.waitFor(5, TimeUnit.SECONDS);
        if (!compiled) {
            compile.destroyForcibly();
            return new ExecutionResult(null, "Compilation timed out", "TIMEOUT");
        }
        String compileOutput = new String(compile.getInputStream().readAllBytes());
        if (compile.exitValue() != 0) {
            return new ExecutionResult(null, compileOutput, "ERROR");
        }

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
        if (!commandAvailable("python3")) {
            return new ExecutionResult(null, "python3 is not installed on server", "ERROR");
        }

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

    private ExecutionResult executeNode(Path filePath, String input) throws Exception {
        if (!commandAvailable("node")) {
            return new ExecutionResult(null, "Node.js is not installed on server", "ERROR");
        }
        Process run = new ProcessBuilder("node", filePath.toString())
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

    private ExecutionResult executeC(Path projectPath, Path filePath, String input) throws Exception {
        if (!commandAvailable("gcc")) {
            return new ExecutionResult(null, "gcc is not installed on server", "ERROR");
        }
        String baseName = stripExtension(filePath.getFileName().toString());
        String outputName = baseName.isEmpty() ? "a.out" : baseName;
        Process compile = new ProcessBuilder("gcc", filePath.getFileName().toString(), "-o", outputName)
                .directory(projectPath.toFile())
                .redirectErrorStream(true)
                .start();
        compile.waitFor(5, TimeUnit.SECONDS);
        String compileOutput = new String(compile.getInputStream().readAllBytes());
        if (compile.exitValue() != 0) {
            return new ExecutionResult(null, compileOutput, "ERROR");
        }
        Process run = new ProcessBuilder("./" + outputName)
                .directory(projectPath.toFile())
                .redirectErrorStream(true)
                .start();
        return runWithInput(run, input);
    }

    private ExecutionResult executeCpp(Path projectPath, Path filePath, String input) throws Exception {
        if (!commandAvailable("g++")) {
            return new ExecutionResult(null, "g++ is not installed on server", "ERROR");
        }
        String baseName = stripExtension(filePath.getFileName().toString());
        String outputName = baseName.isEmpty() ? "a.out" : baseName;
        Process compile = new ProcessBuilder("g++", filePath.getFileName().toString(), "-o", outputName)
                .directory(projectPath.toFile())
                .redirectErrorStream(true)
                .start();
        compile.waitFor(5, TimeUnit.SECONDS);
        String compileOutput = new String(compile.getInputStream().readAllBytes());
        if (compile.exitValue() != 0) {
            return new ExecutionResult(null, compileOutput, "ERROR");
        }
        Process run = new ProcessBuilder("./" + outputName)
                .directory(projectPath.toFile())
                .redirectErrorStream(true)
                .start();
        return runWithInput(run, input);
    }

    private ExecutionResult executeGo(Path projectPath, Path filePath, String input) throws Exception {
        if (!commandAvailable("go")) {
            return new ExecutionResult(null, "Go is not installed on server", "ERROR");
        }
        Process run = new ProcessBuilder("go", "run", filePath.getFileName().toString())
                .directory(projectPath.toFile())
                .redirectErrorStream(true)
                .start();
        return runWithInput(run, input);
    }

    private ExecutionResult executeCSharp(Path projectPath, Path filePath, String input) throws Exception {
        if (commandAvailable("mcs")) {
            String baseName = stripExtension(filePath.getFileName().toString());
            String exeName = baseName.isEmpty() ? "Program.exe" : baseName + ".exe";
            Process compile = new ProcessBuilder("mcs", filePath.getFileName().toString(), "-out:" + exeName)
                    .directory(projectPath.toFile())
                    .redirectErrorStream(true)
                    .start();
            compile.waitFor(5, TimeUnit.SECONDS);
            String compileOutput = new String(compile.getInputStream().readAllBytes());
            if (compile.exitValue() != 0) {
                return new ExecutionResult(null, compileOutput, "ERROR");
            }
            if (!commandAvailable("mono")) {
                return new ExecutionResult(null, "mono is not installed on server", "ERROR");
            }
            Process run = new ProcessBuilder("mono", exeName)
                    .directory(projectPath.toFile())
                    .redirectErrorStream(true)
                    .start();
            return runWithInput(run, input);
        }
        if (commandAvailable("csc")) {
            String baseName = stripExtension(filePath.getFileName().toString());
            String exeName = baseName.isEmpty() ? "Program.exe" : baseName + ".exe";
            Process compile = new ProcessBuilder("csc", filePath.getFileName().toString(), "-out:" + exeName)
                    .directory(projectPath.toFile())
                    .redirectErrorStream(true)
                    .start();
            compile.waitFor(5, TimeUnit.SECONDS);
            String compileOutput = new String(compile.getInputStream().readAllBytes());
            if (compile.exitValue() != 0) {
                return new ExecutionResult(null, compileOutput, "ERROR");
            }
            if (commandAvailable("mono")) {
                Process run = new ProcessBuilder("mono", exeName)
                        .directory(projectPath.toFile())
                        .redirectErrorStream(true)
                        .start();
                return runWithInput(run, input);
            }
            if (commandAvailable("dotnet")) {
                Process run = new ProcessBuilder("dotnet", exeName)
                        .directory(projectPath.toFile())
                        .redirectErrorStream(true)
                        .start();
                return runWithInput(run, input);
            }
            return new ExecutionResult(null, "C# runtime not installed on server", "ERROR");
        }
        return new ExecutionResult(null, "C# compiler not installed on server", "ERROR");
    }

    private ExecutionResult runWithInput(Process run, String input) throws Exception {
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

    private boolean commandAvailable(String command) {
        try {
            Process proc = new ProcessBuilder("sh", "-c", "command -v " + command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = proc.waitFor(1, TimeUnit.SECONDS);
            return finished && proc.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String stripExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx == -1 ? filename : filename.substring(0, idx);
    }
}
