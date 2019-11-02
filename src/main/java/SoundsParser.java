import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.List;

public class SoundsParser {
    private File rootDirectory = new File("./AmericanSounds");
    private String baseUrl = "https://pronuncian.com";
    private File errorFile = new File(rootDirectory, "errorFile.txt");
    private PrintWriter out;

    public SoundsParser() {
    }

    private void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                if (!Files.isSymbolicLink(f.toPath())) {
                    deleteDir(f);
                }
            }
        }
        file.delete();
    }

    public void startParse() {
        deleteDir(rootDirectory);
        boolean newRootDir = rootDirectory.mkdir();
        File resultDirectory = createDir(rootDirectory, "result");
        try {
            boolean newErrorFile = errorFile.createNewFile();
            FileWriter fw = new FileWriter(errorFile, true);
            BufferedWriter bw = new BufferedWriter(fw);
            out = new PrintWriter(bw);
            Document doc = Jsoup.connect(baseUrl + "/sounds").get();
            Element allSounds = doc
                    .select("html")
                    .select("body")
                    .select("#canvas-wrapper")
                    .select("#canvas")
                    .select("#page-body-wrapper")
                    .select("#page-body")
                    .select("#content-wrapper")
                    .select("#content")
                    .select(".main-content")
                    .select(".sqs-layout.sqs-grid-12.columns-12")
                    .select(".row.sqs-row")
                    .select(".col.sqs-col-12.span-12")
                    .select(".sqs-block.html-block.sqs-block-html")
                    .select(".sqs-block-content")
                    .first();
            Element vowelSounds = allSounds.child(3);
            Element rControlledVowels = allSounds.child(5);
            Element consonantSounds = allSounds.child(7);
            forEachElement(vowelSounds, createDir(resultDirectory, "vowelSounds"));
            forEachElement(rControlledVowels, createDir(resultDirectory, "rControlledVowels"));
            forEachElement(consonantSounds, createDir(resultDirectory, "consonantSounds"));

        } catch (IOException e) {
            out.println(String.format("Общая ошибка = %s", e.getMessage()));
            deleteDir(resultDirectory);
            e.printStackTrace();
        }
    }

    private void forEachElement(Element element, File parentDirectory) throws IOException {
        for (int i = 0; i < element.childNodeSize(); i++) {
            parseSoundRow(element.child(i), parentDirectory);
        }
    }

    private void parseSoundRow(Element element, File parentDirectory) throws IOException {
        String soundName = element.textNodes().get(0).toString();
        String soundTranscription = element.child(0).childNodes().get(0).toString().replaceAll("/", "");
        File soundDirectory = createDir(parentDirectory, soundTranscription);
        parsePronunciationPage(element, soundDirectory, soundTranscription);
        parseSpellingPage(element, soundDirectory);
        parsePracticePage(element, soundDirectory);
    }

    private void parsePronunciationPage(Element element, File soundDirectory, String soundName) throws IOException {
        String pronunciation = element.child(1).childNodes().get(0).toString();
        File pronunciationDirectory = createDir(soundDirectory, pronunciation);
        String pronunciationUrl = element.child(1).attr("href");
        Document doc = Jsoup.connect(baseUrl + pronunciationUrl).get();
        Elements allPage = doc.body().select("*");
        String imageUrl = allPage.select("img[src$=.gif]").attr("src");
        loadPhoto(imageUrl, pronunciationDirectory, soundName);
        String soundAudioUrl = allPage.select("div.sqs-audio-embed").attr("data-url");
        loadAudio(soundAudioUrl, pronunciationDirectory, soundName);
    }

    private void parseSpellingPage(Element element, File soundDirectory) throws IOException {
        String spelling = element.child(2).childNodes().get(0).toString();
        File spellingDirectory = createDir(soundDirectory, spelling);
        String spellingUrl = element.child(2).attr("href");
        Document doc = Jsoup.connect(baseUrl + spellingUrl).get();
        Elements allMainContent = doc.body().select("*").select("div.main-content").select("*");
        Elements transcriptionBlocks = allMainContent.select("[data-block-type='2']");
        Elements audioBlocks = allMainContent.select("[data-block-type='41']");
        audioBlocks.forEach(audioItem -> {
            Elements audioBlock = audioItem.select("*").select("div.sqs-audio-embed");
            String word = audioBlock.attr("data-title");
            String audioUrl = audioBlock.attr("data-url");
            String transcriptionHtml = "";
            String transcriptionText = "";
            for (Element transcriptionItem : transcriptionBlocks) {
                Elements blocks = transcriptionItem.select("div.sqs-block-content").select("p");
                Element transcriptionBlock;
                if (blocks.isEmpty()) {
                    continue;
                }
                if (blocks.size() == 1)
                    transcriptionBlock = blocks.first();
                else transcriptionBlock = blocks.last();
                String itemTranscriptionHtml = transcriptionBlock.html();
                itemTranscriptionHtml = itemTranscriptionHtml.replaceAll("&nbsp;", " ");
                if (itemTranscriptionHtml.length() < 4)
                    continue;
                itemTranscriptionHtml = itemTranscriptionHtml.substring(3);
                String itemTranscriptionText = Jsoup.parse(itemTranscriptionHtml).text();
                if (itemTranscriptionText.contains(word)) {
                    transcriptionHtml = itemTranscriptionHtml;
                    transcriptionText = itemTranscriptionText;
                    break;
                }
            }
            loadAudio(audioUrl, spellingDirectory, word);
            log(String.format("Download %s %s %s %s from %s", spelling, word, transcriptionText, transcriptionHtml, audioItem.baseUri()));
        });
    }

    private void parsePracticePage(Element element, File soundDirectory) throws IOException {
        String practice = element.child(3).childNodes().get(0).toString();
        File practiceDirectory = createDir(soundDirectory, practice);
        String practiceUrl = element.child(3).attr("href");
        Document doc = Jsoup.connect(baseUrl + practiceUrl).get();
        List<Node> allMainContent = doc.select("*")
                .select("div.main-content")
                .get(0)
                .child(0)
                .child(0)
                .child(0)
                .childNodes();
        File soundPositionDirectory = null;
        for (int i = 0; i < allMainContent.size(); i++) {
            Node node = allMainContent.get(i);
            if (!(node instanceof Element))
                continue;
            Element elementItem = (Element) node;
            elementItem.select("*").select("div.sqs-block-content");
            Elements soundPositionTitle = elementItem.select("*").select("h2");
            if (!soundPositionTitle.isEmpty())
                soundPositionDirectory = createDir(practiceDirectory, Jsoup.parse(soundPositionTitle.html()).text());
            Elements audioElements = elementItem.select("*").select("div.sqs-audio-embed");
            for (Element audio : audioElements) {
                String word = audio.attr("data-title");
                String audioUrl = audio.attr("data-url");
                if (soundPositionDirectory != null) {
                    loadAudio(audioUrl, soundPositionDirectory, word);
                    log(String.format("Download %s for %s in %s from %s", word, soundDirectory.getName(), soundPositionDirectory.getName(), audio.baseUri()));
                }
            }
        }
    }

    private final long debounce = 2000;

    private void loadAudio(String audioUrl, File parentDirectory, String name) {
        try {
            ReadableByteChannel readableByteChannel = Channels.newChannel(new URL(audioUrl).openStream());
            String format = getFileFormat(audioUrl);
            name = name
                    .replaceAll("/", "%2f")
                    .replaceAll("\\\\", "%5c");
            FileOutputStream fileOutputStream = new FileOutputStream(new File(parentDirectory, name + "." + format));
            fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            Thread.sleep(debounce);
        } catch (IOException e) {
            out.println(String.format("photoId = %s Ошибка скачивания Ошибка: %s\n", name, e.getMessage()));
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void loadPhoto(String imageUrl, File parentDirectory, String name) {
        try {
            BufferedImage bufferedImage = ImageIO.read(new URL(imageUrl));
            String format = getFileFormat(imageUrl);
            name = name
                    .replaceAll("/", "%2f")
                    .replaceAll("\\\\", "%5c");
            ImageIO.write(bufferedImage, format, new File(parentDirectory, name + "." + format));
            Thread.sleep(debounce);
        } catch (IOException e) {
            out.println(String.format("photoId = %s Ошибка скачивания Ошибка: %s\n", name, e.getMessage()));
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private String getFileFormat(String largestSizeUrl) {
        int i = largestSizeUrl.lastIndexOf('.');
        String extension = "";
        if (i > 0) {
            extension = largestSizeUrl.substring(i + 1);
        }
        return extension;
    }

    private File createDir(File parent, String child) {
        File result = new File(parent, child);
        if (!result.isDirectory()) {
            result.mkdir();
        }
        return result;
    }

    private void log(String msg) {
        System.out.println("SoundsParser: " + msg);
    }
}
