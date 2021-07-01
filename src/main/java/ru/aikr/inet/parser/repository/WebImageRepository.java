package ru.aikr.inet.parser.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ru.aikr.inet.parser.domain.WebImage;

public interface WebImageRepository extends MongoRepository<WebImage, String> {

}
