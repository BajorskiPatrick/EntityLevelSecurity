package com.example.demo.controller;

import com.example.demo.model.Note;
import com.example.demo.service.NoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate; // Do wstawiania ACL

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;
    private final JdbcTemplate jdbcTemplate; // Hack do wstawiania rekordów ACL

    public NoteController(NoteService noteService, JdbcTemplate jdbcTemplate) {
        this.noteService = noteService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<Note> getNotes() {
        // Wywołuje metodę chronioną przez @RequiresAcl
        return noteService.getAllMyNotes();
    }

    @PostMapping
    public Note createNote(@RequestBody Note note, @RequestHeader(value = "X-User-Id", defaultValue = "-1") Long userId) {
        if (userId == -1) {
            throw new IllegalArgumentException("userId is null");
        }

        Note saved = noteService.createNote(note);

        // AUTOMATYCZNE NADANIE UPRAWNIEŃ (Symulacja logiki biznesowej)
        // Normalnie robiłby to jakiś serwis "ShareService" albo Trigger
        // Wstawiamy rekord do tabeli biblioteki ELS
        String sql = "INSERT INTO els_acl_table (user_id, table_name, row_id) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, userId, "notes", saved.getId());

        return saved;
    }

    // Endpoint pomocniczy do współdzielenia notatki (Share)
    @PostMapping("/{noteId}/share")
    public ResponseEntity<?> shareNote(@PathVariable Long noteId, @RequestParam Long targetUser) {
        String sql = "INSERT INTO els_acl_table (user_id, table_name, row_id) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, targetUser, "notes", noteId);
        return ResponseEntity.ok(Map.of("message", "Note shared with " + targetUser));
    }
}