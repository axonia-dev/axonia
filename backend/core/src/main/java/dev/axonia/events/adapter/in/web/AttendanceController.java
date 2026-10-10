package dev.axonia.events.adapter.in.web;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.events.api.Attendance;
import dev.axonia.events.application.AttendanceService;
import dev.axonia.shared.api.web.Pageables;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}/sessions/{id}/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping
    Page<Attendance> list(@PathVariable Long id,
            @PageableDefault(size = 50, sort = "created_at", direction = Sort.Direction.ASC) Pageable pageable) {

        return attendanceService.list(id, Pageables.requireSortIn(pageable, Set.of("created_at")));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Attendance signal(@PathVariable Long id) {

        return attendanceService.signal(id);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsignal(@PathVariable Long id) {

        attendanceService.unsignal(id);
    }
}
