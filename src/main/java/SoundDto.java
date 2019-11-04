import java.util.ArrayList;
import java.util.List;

public class SoundDto {
    public String transcription;
    public String name;
    public String description = "";
    public String audioPath = "";
    public String photoPath = "";
    public List<SpellingWordDto> spellingWordList = new ArrayList<>();
    public SoundPracticeWords soundPracticeWords = new SoundPracticeWords();
    public SoundType soundType;

    public static class SoundPracticeWords {
        public List<PracticeWordDto> beginningSound = new ArrayList<>();
        public List<PracticeWordDto> endSound = new ArrayList<>();
        public List<PracticeWordDto> middleSound = new ArrayList<>();
    }

    public enum SoundType {
        consonantSounds, rControlledVowels, vowelSounds
    }
}
