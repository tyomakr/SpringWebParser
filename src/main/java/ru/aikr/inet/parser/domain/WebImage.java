package ru.aikr.inet.parser.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;



@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebImage {

    private String id;
    private String directLink;

    public WebImage(String directLink) {
        this.directLink = directLink;
    }
}
