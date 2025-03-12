package ru.aikr.inet.parser.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Objects;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WebImage {
    private String id;
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