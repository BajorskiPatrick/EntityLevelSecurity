package com.example.demo.config;

import com.example.demo.model.Note;
import com.example.demo.model.User;
import com.example.demo.repository.NoteRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    public DataSeeder(NoteRepository noteRepository, UserRepository userRepository, JdbcTemplate jdbcTemplate) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() == 0) {
            System.out.println("--- SEEDOWANIE UŻYTKOWNIKÓW I NOTATEK ---");

            // 1. Tworzenie użytkowników (Baza nada im ID, np. 1, 2, 3)
            User admin = userRepository.save(new User("admin", "admin123"));
            User jan = userRepository.save(new User("jan", "haslo123"));
            User anna = userRepository.save(new User("anna", "anna123"));

            // 2. Tworzenie notatek
            Note n1 = noteRepository.save(Note.builder().title("Tajny plan").content("Dla Jana").category("Work").build());
            Note n2 = noteRepository.save(Note.builder().title("Lista zakupów").content("Dla Anny").category("Home").build());
            Note n3 = noteRepository.save(Note.builder().title("Wspólny projekt").content("Dla wszystkich").category("Work").build());

            // 3. Przydzielanie uprawnień ACL używając PRAWDZIWYCH ID

            // Admin widzi wszystko
            addAcl(admin.getId(), n1.getId());
            addAcl(admin.getId(), n2.getId());
            addAcl(admin.getId(), n3.getId());

            // Jan widzi n1 i n3
            addAcl(jan.getId(), n1.getId());
            addAcl(jan.getId(), n3.getId());

            // Anna widzi n2 i n3
            addAcl(anna.getId(), n2.getId());
            addAcl(anna.getId(), n3.getId());

            System.out.println("--- DANE ZAŁADOWANE: Jan ID=" + jan.getId() + ", Anna ID=" + anna.getId() + " ---");
        }
    }

    private void addAcl(Long userId, Long rowId) {
        // Zapisujemy userId jako String w tabeli ACL
        jdbcTemplate.update("INSERT INTO els_acl_table (user_id, table_name, row_id) VALUES (?, 'notes', ?)",
                userId, rowId);
    }
}