package com.codesphere.backend.controller;

import com.codesphere.backend.dto.ApiResponse;
import com.codesphere.backend.dto.FileRequest;
import com.codesphere.backend.entity.FileEntity;
import com.codesphere.backend.entity.ProjectEntity;
import com.codesphere.backend.entity.UserEntity;
import com.codesphere.backend.repository.FileRepository;
import com.codesphere.backend.repository.ProjectRepository;
import com.codesphere.backend.repository.UserRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectName}/files")
public class FileController {

	private static final String BASE_DIR =
	        System.getProperty("user.dir") + "/codesphere_workspace";


    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;

    public FileController(ProjectRepository projectRepository,
                          UserRepository userRepository,
                          FileRepository fileRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.fileRepository = fileRepository;
    }

    @PutMapping
    public ResponseEntity<ApiResponse<String>> saveFile(
            @PathVariable String projectName,
            @RequestBody FileRequest request) {

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
            // 3️⃣ Write file to filesystem
            Path projectPath = Path.of(BASE_DIR, projectName);
            Files.createDirectories(projectPath);

            Path filePath = projectPath.resolve(request.getFilename());
            Files.writeString(filePath, request.getContent());
           
            // 4️⃣ Save / update file metadata in DB
            FileEntity file = new FileEntity();
            file.setFilename(request.getFilename());
            file.setProject(project);
            String language = getLanguageFromFilename(request.getFilename());
            file.setLanguage(language);
            fileRepository.save(file);

            return ResponseEntity.ok(
                    new ApiResponse<>(true, "File saved successfully", request.getFilename())
            );

        } catch (Exception e) {
            e.printStackTrace(); // ⭐ IMPORTANT
            return ResponseEntity.internalServerError()
                .body(new ApiResponse<>(false, "Failed to save file", null));
        }

    }
    private String getLanguageFromFilename(String filename) {
        int index = filename.lastIndexOf('.');
        if (index == -1) return "unknown";

        return switch (filename.substring(index + 1).toLowerCase()) {
            case "java" -> "java";
            case "py" -> "python";
            case "js" -> "javascript";
            default -> "unknown";
        };
    }
    
    @GetMapping
    public ResponseEntity<ApiResponse<List<String>>> listFiles(
            @PathVariable String projectName) {

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

        // 3️⃣ Fetch files ONLY for this project
        List<String> files = fileRepository.findByProject(project)
                .stream()
                .map(FileEntity::getFilename)
                .toList();

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Files fetched", files)
        );
    }


}
