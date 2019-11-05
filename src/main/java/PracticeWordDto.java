public class PracticeWordDto {
    public String sound;
    public String name;
    public String audioPath = "";
    public SoundPositionType soundPositionType;

    public enum SoundPositionType {
        beginningSound, middleSound, endSound
    }
}
