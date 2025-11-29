package com.example.demo.service;

import com.els.annotation.RequiresAcl; // Twoja biblioteka!
import com.example.demo.model.Note;
import com.example.demo.repository.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NoteService {

    private final NoteRepository noteRepository;

    public NoteService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    /**
     * Ta metoda jest chroniona przez bibliotekę ELS.
     * SQL zostanie zmodyfikowany, aby zwrócić tylko notatki przypisane do usera.
     */
    @Transactional(readOnly = true)
    @RequiresAcl
    public List<Note> getAllMyNotes() {
        // Programista pisze "daj wszystko", a biblioteka robi "daj moje"
        return noteRepository.findAll();
    }

    @Transactional
    public Note createNote(Note note) {
        // Tutaj normalny zapis
        return noteRepository.save(note);
    }
}