package com.codesphere.backend.controller;

import com.codesphere.backend.dto.ApiResponse;
import com.codesphere.backend.dto.ProjectRequest;
import com.codesphere.backend.entity.ProjectEntity;
import com.codesphere.backend.repository.ProjectRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private static final String BASE_DIR = "codesphere_workspace";
    private final ProjectRepository projectRepository;

    public ProjectController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<String>> createProject(
            @RequestBody ProjectRequest request) {

        String projectName = request.getName().trim();

        // 1 Check DB
        if (projectRepository.existsByName(projectName)) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Project already exists", null));
        }

        // 2 Save in DB
        ProjectEntity project = new ProjectEntity();
        project.setName(projectName);
        projectRepository.save(project);

        // 3 Create folder
        File projectDir = new File(BASE_DIR, projectName);
        projectDir.mkdirs();

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Project created successfully", projectName)
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<String>>> listProjects() {

        List<String> projects = projectRepository.findAll()
                .stream()
                .map(ProjectEntity::getName)
                .toList();

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Projects fetched", projects)
        );
    }
}
