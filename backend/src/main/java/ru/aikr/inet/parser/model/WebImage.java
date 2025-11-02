package ru.aikr.inet.parser.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WebImage {
    private String id;
    
    @NotBlank(message = "Прямая ссылка на изображение обязательна")
    @Pattern(regexp = "^(http|https)://.+", message = "Ссылка должна начинаться с http:// или https://")
    private String directLink;

    public WebImage(String directLink) {
        this.directLink = directLink;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WebImage)) return false;
        return Objects.equals(directLink, ((WebImage) o).directLink);
    }

    @Override
    public int hashCode() {
        return Objects.hash(directLink);
    }
}