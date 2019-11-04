import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SoundsParser {
    private File rootDirectory = new File("./AmericanSounds");
    private String baseUrl = "https://pronuncian.com";
    private File errorFile = new File(rootDirectory, "errorFile.txt");
    private PrintWriter out;
    private Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

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

    public void startParse() throws FileNotFoundException {
        /*File file = new File(new File(rootDirectory, "json"), "vowelSoundsJson");
        List<SoundDto> yourClassList = new Gson().fromJson(new FileReader(file), listType);*/
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

            List<SoundDto> vowelSoundsList =
                    forEachElement(vowelSounds, createDir(resultDirectory, "vowelSounds"), SoundDto.SoundType.vowelSounds);
            List<SoundDto> rControlledVowelsList =
                    forEachElement(rControlledVowels, createDir(resultDirectory, "rControlledVowels"), SoundDto.SoundType.rControlledVowels);
            List<SoundDto> consonantSoundsList =
                    forEachElement(consonantSounds, createDir(resultDirectory, "consonantSounds"), SoundDto.SoundType.consonantSounds);

            File jsonDirectory = createDir(rootDirectory, "json");
            soundListToJson(jsonDirectory, vowelSoundsList, "vowelSoundsJson");
            soundListToJson(jsonDirectory, rControlledVowelsList, "rControlledVowels");
            soundListToJson(jsonDirectory, consonantSoundsList, "consonantSounds");

            log("FINISH");

        } catch (IOException e) {
            out.println(String.format("Общая ошибка = %s", e.getMessage()));
            deleteDir(resultDirectory);
            e.printStackTrace();
        }
    }

    private void soundListToJson(File parentDirectory, List<SoundDto> list, String type) {
//        File soundsJsonDir = createDir(parentDirectory, type);
        list.forEach(soundDto -> {
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(new File(parentDirectory, soundDto.transcription)));
                writer.write(gson.toJson(soundDto));
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private List<SoundDto> forEachElement(Element element, File parentDirectory, SoundDto.SoundType soundType) throws IOException {
        List<SoundDto> soundDtoList = new ArrayList<>();
        for (int i = 0; i < element.childNodeSize(); i++) {
            SoundDto soundDto = new SoundDto();
            soundDto.soundType = soundType;
            parseSoundRow(element.child(i), parentDirectory, soundDto);
            soundDtoList.add(soundDto);
        }
        return soundDtoList;
    }

    private void parseSoundRow(Element element, File parentDirectory, SoundDto soundDto) throws IOException {
        soundDto.name = element.textNodes().get(0).toString();
        String soundTranscription = element.child(0).childNodes().get(0).toString().replaceAll("/", "");
        soundDto.transcription = soundTranscription;
        File soundDirectory = createDir(parentDirectory, soundTranscription);
        parsePronunciationPage(element, soundDirectory, soundTranscription, soundDto);
        parseSpellingPage(element, soundDirectory, soundDto);
        parsePracticePage(element, soundDirectory, soundDto);
    }

    private void parsePronunciationPage(Element element, File soundDirectory, String soundName, SoundDto soundDto) throws IOException {
        String pronunciation = element.child(1).childNodes().get(0).toString();
        File pronunciationDirectory = createDir(soundDirectory, pronunciation);
        String pronunciationUrl = element.child(1).attr("href");
        Document doc = Jsoup.connect(baseUrl + pronunciationUrl).get();
        Elements allPage = doc.body().select("*");
        String imageUrl = allPage.select("img[src$=.gif]").attr("src");
        soundDto.photoPath =
                loadAudio(imageUrl, pronunciationDirectory, soundName).getPath();
        String soundAudioUrl = allPage.select("div.sqs-audio-embed").attr("data-url");
        soundDto.audioPath =
                loadAudio(soundAudioUrl, pronunciationDirectory, soundName).getPath();
    }

    private void parseSpellingPage(Element element, File soundDirectory, SoundDto soundDto) throws IOException {
        String spelling = element.child(2).childNodes().get(0).toString();
        File spellingDirectory = createDir(soundDirectory, spelling);
        String spellingUrl = element.child(2).attr("href");
        Document doc = Jsoup.connect(baseUrl + spellingUrl).get();
        Elements allMainContent = doc.body().select("*").select("div.main-content").select("*");
        Elements transcriptionBlocks = allMainContent.select("[data-block-type='2']");
        Elements audioBlocks = allMainContent.select("[data-block-type='41']");
        ExecutorService service = Executors.newCachedThreadPool();
        audioBlocks.forEach(audioItem -> {
            service.submit(() -> {
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
                File newAudio = loadAudio(audioUrl, spellingDirectory, word);
                if (newAudio != null) {
                    SpellingWordDto newWord = new SpellingWordDto();
                    newWord.audioPath = newAudio.getPath();
                    newWord.name = word;
                    newWord.transcription = transcriptionHtml;
                    soundDto.spellingWordList.add(newWord);
                }
                log(String.format("Download %s %s %s %s from %s", spelling, word, transcriptionText, transcriptionHtml, audioItem.baseUri()));
            });
        });
        try {
            service.shutdown();
            service.awaitTermination(30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void parsePracticePage(Element element, File soundDirectory, SoundDto soundDto) throws IOException {
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
            Element soundPositionTitle = elementItem.select("*").select("h2").last();
            if (soundPositionTitle != null) {
                soundPositionDirectory = createDir(practiceDirectory, Jsoup.parse(soundPositionTitle.html()).text());
            }
            Elements audioElements = elementItem.select("*").select("div.sqs-audio-embed");
            ExecutorService service = Executors.newCachedThreadPool();
            for (Element audio : audioElements) {
                File finalSoundPositionDirectory = soundPositionDirectory;
                service.submit(() -> {
                    String word = audio.attr("data-title");
                    String audioUrl = audio.attr("data-url");
                    if (finalSoundPositionDirectory != null) {
                        File newAudio = loadAudio(audioUrl, finalSoundPositionDirectory, word);
                        if (newAudio != null) {
                            PracticeWordDto newWord = new PracticeWordDto();
                            newWord.name = word;
                            newWord.audioPath = newAudio.getPath();
                            switch (finalSoundPositionDirectory.getName()) {
                                case "Beginning sound":
                                    soundDto.soundPracticeWords.beginningSound.add(newWord);
                                    break;
                                case "End Sound":
                                    soundDto.soundPracticeWords.endSound.add(newWord);
                                    break;
                                case "Middle Sound":
                                    soundDto.soundPracticeWords.middleSound.add(newWord);
                                    break;
                            }
                        }
                        log(String.format("Download %s for %s in %s from %s", word, soundDirectory.getName(), finalSoundPositionDirectory.getName(), audio.baseUri()));
                    }
                });
            }
            try {
                service.shutdown();
                service.awaitTermination(30, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private final long debounceAfterError = TimeUnit.SECONDS.toMillis(5);
    private final long debounce = TimeUnit.SECONDS.toMillis(0);

    private File loadAudio(String audioUrl, File parentDirectory, String name) {
        File result;
        String format = getFileFormat(audioUrl);
        name = name
                .replaceAll("/", "%2f")
                .replaceAll("\\\\", "%5c");
        try {
            Thread.sleep(debounce);
            File newFile = new File(parentDirectory, name + "." + format);
            ReadableByteChannel readableByteChannel = Channels.newChannel(new URL(audioUrl).openStream());
            FileOutputStream fileOutputStream = new FileOutputStream(newFile);
            fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            result = newFile;
        } catch (IOException e) {
            out.println(String.format("photoId = %s Ошибка скачивания Ошибка: %s\n", name, e.getMessage()));
            log(String.format("Error download %s %s", e.getMessage(), audioUrl));
            if (e.getMessage().contains("429")) {
                try {
                    Thread.sleep(debounceAfterError);
                    log(String.format("Try download again %s", audioUrl));
                    result = loadAudio(audioUrl, parentDirectory, name);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                    result = null;
                }
            } else {
                e.printStackTrace();
                result = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            result = null;
        }
        return result;
//        return new File(parentDirectory, name + "." + format);
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

    private File createFile(File parent, String child) throws IOException {
        File result = new File(parent, child);
        if (!result.exists())
            result.createNewFile();
        return result;
    }

    private void log(String msg) {
        System.out.println("SoundsParser: " + msg);
    }
}
