package com.manas.engineer;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/v1/software-engineers")
public class SoftwareEngineerController {

    @GetMapping
    public List<SoftwareEngineer> getEngineers() {
        return List.of(
                new SoftwareEngineer(1, "John Doe", "JS, Node, React, TailwindCss"),
                new SoftwareEngineer(2, "Jane Doe", "Java, Spring, Spring Boot")
        );
    }
}
