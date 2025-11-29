package com.example.demo.model;

import jakarta.persistence.*;

@Entity
@Table(name = "notes")
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 1000)
    private String content;

    private String category;

    // 1. Konstruktor bezargumentowy (wymagany przez JPA)
    public Note() {
    }

    // 2. Prywatny konstruktor przyjmujący Buildera (używany przez wzorzec)
    private Note(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.content = builder.content;
        this.category = builder.category;
    }

    // 3. Gettery i Settery (wymagane przez JPA i Jacksona do JSON)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    // 4. Metoda statyczna startująca budowanie
    public static Builder builder() {
        return new Builder();
    }

    // ==========================================
    // WZORZEC BUILDER (Manualna implementacja)
    // ==========================================
    public static class Builder {
        // Pola buildera muszą odpowiadać polom klasy
        private Long id;
        private String title;
        private String content;
        private String category;

        // Metody ustawiające wartości i zwracające "this" (Fluent Interface)
        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        // Metoda kończąca budowanie
        public Note build() {
            return new Note(this);
        }
    }

    // Opcjonalnie toString (przydatne do logowania)
    @Override
    public String toString() {
        return "Note{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", category='" + category + '\'' +
                '}';
    }
}