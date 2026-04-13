package ratscrewd.util;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

/**
 * Handles loading and playing sound effects for the game.
 */
public class SoundManager {
    
    /**
     * Plays a sound effect from the sounds directory.
     * This method is non-blocking - sound plays in background.
     * 
     * @param soundFileName name of the .wav file (e.g., "slap.wav")
     */
    public static void playSound(String soundFileName) {
        try {
            // Construct the file path
            String soundPath = "sounds/" + soundFileName;
            File soundFile = new File(soundPath);
            
            if (!soundFile.exists()) {
                System.err.println("Sound file not found: " + soundPath);
                return;
            }
            
            // Load the sound file
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            
            // Play the sound
            clip.start();
            
            // Clean up after sound finishes playing
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                    try {
                        audioStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            
        } catch (UnsupportedAudioFileException e) {
            System.err.println("Unsupported audio file format: " + soundFileName);
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("Error loading sound file: " + soundFileName);
            e.printStackTrace();
        } catch (LineUnavailableException e) {
            System.err.println("Audio line unavailable for: " + soundFileName);
            e.printStackTrace();
        }
    }
    
    /**
     * Plays a sound effect with volume control.
     * 
     * @param soundFileName name of the .wav file
     * @param volume volume level from 0.0 (silent) to 1.0 (full volume)
     */
    public static void playSound(String soundFileName, float volume) {
        try {
            String soundPath = "sounds/" + soundFileName;
            File soundFile = new File(soundPath);
            
            if (!soundFile.exists()) {
                System.err.println("Sound file not found: " + soundPath);
                return;
            }
            
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            
            // Set volume
            FloatControl volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float min = volumeControl.getMinimum();
            float max = volumeControl.getMaximum();
            float gain = min + (max - min) * volume;
            volumeControl.setValue(gain);
            
            clip.start();
            
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                    try {
                        audioStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            
        } catch (Exception e) {
            System.err.println("Error playing sound with volume: " + soundFileName);
            e.printStackTrace();
        }
    }
}
