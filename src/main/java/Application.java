import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;

public class Application {
    public static void main(String[] args) throws IOException {
        new SoundsParser().startParse();
    }
}
