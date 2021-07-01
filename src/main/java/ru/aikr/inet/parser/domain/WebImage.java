package ru.aikr.inet.parser.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "webImages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebImage {

    @Id
    private String id;
    @Field(name = "directLink")
    private String directLink;

    public WebImage(String directLink) {
        this.directLink = directLink;
    }
}
