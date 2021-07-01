package parser;

import ru.aikr.inet.comimgparser.domain.WebImage;

import java.util.List;

public interface WebImageParserService {

    List<WebImage> getImageLinksFromPages(int pageBegin, int pageEnd);
}
