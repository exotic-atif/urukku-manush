package atifscodeworks.urukkumanush;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AudioManager {
    private static final String TAG = "AudioManager";

    private final Context context;
    private final Random random = new Random();

    private SoundPool soundPool;
    private final List<Integer> playSoundIds = new ArrayList<>();
    private final List<Integer> jumpSoundIds = new ArrayList<>();
    private final List<Integer> loseSoundIds = new ArrayList<>();
    private final List<Integer> clickSoundIds = new ArrayList<>();
    private final List<Integer> passSoundIds = new ArrayList<>();

    private MediaPlayer introPlayer;
    private MediaPlayer bgmPlayer;

    private boolean sfxEnabled = true;
    private boolean bgmEnabled = true;
    private float bgmVolume = 0.8f; // 80%

    private final List<String> introFiles = new ArrayList<>();
    private final List<String> bgmFiles = new ArrayList<>();

    public AudioManager(Context context) {
        this.context = context.getApplicationContext();
        initSoundPool();
        listAudioFiles();
    }

    private void initSoundPool() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(12)
                .setAudioAttributes(audioAttributes)
                .build();

        loadSfxFolder("audios/play", playSoundIds);
        loadSfxFolder("audios/jump", jumpSoundIds);
        loadSfxFolder("audios/lose", loseSoundIds);
        loadSfxFolder("audios/click", clickSoundIds);
        loadSfxFolder("audios/pass", passSoundIds);
    }

    private void loadSfxFolder(String folder, List<Integer> soundList) {
        try {
            String[] files = context.getAssets().list(folder);
            if (files != null) {
                for (String file : files) {
                    if (file.endsWith(".mp3") || file.endsWith(".wav") || file.endsWith(".ogg")) {
                        try (AssetFileDescriptor afd = context.getAssets().openFd(folder + "/" + file)) {
                            int soundId = soundPool.load(afd, 1);
                            soundList.add(soundId);
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed loading sfx from " + folder, e);
        }
    }

    private void listAudioFiles() {
        try {
            String[] intros = context.getAssets().list("audios/intro");
            if (intros != null) {
                for (String f : intros) {
                    if (f.endsWith(".mp3") || f.endsWith(".wav") || f.endsWith(".ogg")) {
                        introFiles.add("audios/intro/" + f);
                    }
                }
            }

            String[] bgms = context.getAssets().list("audios/bgm");
            if (bgms != null) {
                for (String f : bgms) {
                    if (f.endsWith(".mp3") || f.endsWith(".wav") || f.endsWith(".ogg")) {
                        bgmFiles.add("audios/bgm/" + f);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed listing music files", e);
        }
    }

    public void playIntroMusic() {
        if (!bgmEnabled || introFiles.isEmpty()) return;
        stopIntroMusic();
        try {
            String chosen = introFiles.get(random.nextInt(introFiles.size()));
            introPlayer = new MediaPlayer();
            AssetFileDescriptor afd = context.getAssets().openFd(chosen);
            introPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            introPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            introPlayer.setVolume(bgmVolume, bgmVolume);
            introPlayer.setOnCompletionListener(mp -> {
                mp.release();
                if (introPlayer == mp) introPlayer = null;
            });
            introPlayer.prepare();
            introPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Error playing intro music", e);
        }
    }

    public void stopIntroMusic() {
        if (introPlayer != null) {
            try {
                if (introPlayer.isPlaying()) {
                    introPlayer.stop();
                }
                introPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping intro music", e);
            }
            introPlayer = null;
        }
    }

    private int currentLoseStreamId = 0;
    private String lastPlayedBgm = null;

    public void playRandomPlaySound() {
        if (!sfxEnabled || playSoundIds.isEmpty()) return;
        int soundId = playSoundIds.get(random.nextInt(playSoundIds.size()));
        soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
    }

    public void playJumpSound() {
        if (!sfxEnabled || jumpSoundIds.isEmpty()) return;
        int soundId = jumpSoundIds.get(random.nextInt(jumpSoundIds.size()));
        soundPool.play(soundId, 0.9f, 0.9f, 1, 0, 1.0f);
    }

    public void playLoseSound() {
        if (!sfxEnabled || loseSoundIds.isEmpty()) return;
        stopLoseSound();
        int soundId = loseSoundIds.get(random.nextInt(loseSoundIds.size()));
        if (soundPool != null) {
            currentLoseStreamId = soundPool.play(soundId, 1.0f, 1.0f, 2, 0, 1.0f);
        }
    }

    public void stopLoseSound() {
        if (currentLoseStreamId != 0 && soundPool != null) {
            try {
                soundPool.stop(currentLoseStreamId);
            } catch (Exception ignored) {
            }
            currentLoseStreamId = 0;
        }
    }

    public void playClickSound() {
        if (!sfxEnabled || clickSoundIds.isEmpty()) return;
        int soundId = clickSoundIds.get(random.nextInt(clickSoundIds.size()));
        soundPool.play(soundId, 0.85f, 0.85f, 1, 0, 1.0f);
    }

    public void playPassSound() {
        if (!sfxEnabled || passSoundIds.isEmpty()) return;
        int soundId = passSoundIds.get(random.nextInt(passSoundIds.size()));
        soundPool.play(soundId, 0.85f, 0.85f, 1, 0, 1.0f);
    }

    public void startBgm() {
        if (!bgmEnabled || bgmFiles.isEmpty()) return;
        playRandomBgmTrack();
    }

    private void playRandomBgmTrack() {
        stopBgm();
        if (bgmFiles.isEmpty()) return;

        List<String> pool = new ArrayList<>(bgmFiles);
        if (pool.size() > 1 && lastPlayedBgm != null) {
            pool.remove(lastPlayedBgm);
        }
        String chosen = pool.get(random.nextInt(pool.size()));
        lastPlayedBgm = chosen;

        try {
            bgmPlayer = new MediaPlayer();
            AssetFileDescriptor afd = context.getAssets().openFd(chosen);
            bgmPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            bgmPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            bgmPlayer.setVolume(bgmVolume, bgmVolume);
            bgmPlayer.setLooping(false);
            bgmPlayer.setOnCompletionListener(mp -> {
                if (bgmEnabled) {
                    playRandomBgmTrack();
                }
            });
            bgmPlayer.prepare();
            bgmPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Error playing BGM track " + chosen, e);
        }
    }

    public void stopBgm() {
        if (bgmPlayer != null) {
            try {
                if (bgmPlayer.isPlaying()) {
                    bgmPlayer.stop();
                }
                bgmPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping BGM", e);
            }
            bgmPlayer = null;
        }
    }

    public void pauseBgm() {
        if (bgmPlayer != null && bgmPlayer.isPlaying()) {
            bgmPlayer.pause();
        }
    }

    public void resumeBgm() {
        if (bgmPlayer != null && bgmEnabled && !bgmPlayer.isPlaying()) {
            bgmPlayer.start();
        } else if (bgmPlayer == null && bgmEnabled && !bgmFiles.isEmpty()) {
            startBgm();
        }
    }

    public void pauseAll() {
        pauseBgm();
        stopLoseSound();
        if (introPlayer != null && introPlayer.isPlaying()) {
            introPlayer.pause();
        }
        if (soundPool != null) {
            soundPool.autoPause();
        }
    }

    public void resumeAll(boolean inGame) {
        if (soundPool != null) {
            soundPool.autoResume();
        }
        if (inGame) {
            resumeBgm();
        } else {
            if (introPlayer != null && bgmEnabled && !introPlayer.isPlaying()) {
                introPlayer.start();
            }
        }
    }

    public void setSfxEnabled(boolean enabled) {
        this.sfxEnabled = enabled;
        if (!enabled) {
            stopLoseSound();
        }
    }

    public boolean isSfxEnabled() {
        return sfxEnabled;
    }

    public void setBgmEnabled(boolean enabled) {
        this.bgmEnabled = enabled;
        if (!enabled) {
            pauseBgm();
            stopIntroMusic();
        } else {
            resumeBgm();
        }
    }

    public boolean isBgmEnabled() {
        return bgmEnabled;
    }

    public void release() {
        stopIntroMusic();
        stopBgm();
        stopLoseSound();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}
