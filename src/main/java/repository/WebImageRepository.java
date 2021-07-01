package repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ru.aikr.inet.comimgparser.domain.WebImage;

public interface WebImageRepository extends MongoRepository<WebImage, String> {

}
